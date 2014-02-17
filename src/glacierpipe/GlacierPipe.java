/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package glacierpipe;

import glacierpipe.io.IOBuffer;
import glacierpipe.io.InputStreamObserver;
import glacierpipe.io.ObservedInputStream;
import glacierpipe.io.ObservedOutputStream;
import glacierpipe.io.OutputStreamObserver;
import glacierpipe.io.ThrottledInputStream;
import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;
import glacierpipe.security.TreeHashMessageDigest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.glacier.AmazonGlacierClient;
import com.amazonaws.services.glacier.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.glacier.model.CompleteMultipartUploadResult;
import com.amazonaws.services.glacier.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.glacier.model.InitiateMultipartUploadResult;
import com.amazonaws.services.glacier.model.UploadMultipartPartRequest;
import com.amazonaws.services.glacier.model.UploadMultipartPartResult;
import com.amazonaws.util.BinaryUtils;

public class GlacierPipe {

	protected final int maxRetries;

	protected final long partSize;
	protected final IOBuffer buffer;
	protected final GlacierPipeObserver observer;
	protected final ThrottlingStrategy throttlingStrategy;
	
	public GlacierPipe(IOBuffer buffer, GlacierPipeObserver observer, int maxRetries) {
		this(buffer, observer, maxRetries, null);
	}
	
	public GlacierPipe(IOBuffer buffer, GlacierPipeObserver observer, int maxRetries, ThrottlingStrategy throttlingStrategy) {
		long partSize = buffer.getCapacity();

		if (partSize < 0) {
			throw new IllegalArgumentException("partSize too small");
		} else if (partSize > 1024L * 1024L * 1024L * 4L) {
			throw new IllegalArgumentException("partSize larger than 4GB");
		} else if (!isPowerOfTwo(partSize) || partSize % (1024 * 1024) != 0) {
			throw new IllegalArgumentException("partSize not 1MB * 2^n");
		} else if (maxRetries < 1) {
			throw new IllegalArgumentException("maxRetries must be at least 1");
		}

		this.partSize = partSize;
		this.buffer = buffer;
		this.observer = observer;
		this.maxRetries = maxRetries;
		this.throttlingStrategy = throttlingStrategy;
	}

	public String pipe(AmazonGlacierClient client, String vaultName, String archiveDesc, InputStream in) throws IOException {

		long currentPosition = 0;
		int partId = 0;
		
		try {
			byte[] buffer = new byte[4096];
			
			TreeHashMessageDigest completeHash = new TreeHashMessageDigest(MessageDigest.getInstance("SHA-256"));
			in = new DigestInputStream(in, completeHash);
			
			/**** Create an upload ID for the current upload ****/
			InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest().
					withVaultName(vaultName).
					withArchiveDescription(archiveDesc).
					withPartSize(Long.toString(partSize));  

			InitiateMultipartUploadResult result = client.initiateMultipartUpload(request);
			String uploadId = result.getUploadId();

			this.observer.gotUploadId(uploadId);

			/**** While there are still chunks to process ****/
			do {
				TreeHashMessageDigest partHash = new TreeHashMessageDigest(MessageDigest.getInstance("SHA-256"));
				
				// Fill up the buffer
				try (
						OutputStream bufferOut = this.buffer.getOutputStream();
						OutputStream observedOut = new ObservedOutputStream(bufferOut, new BufferingObserver(this.observer, partId));
						DigestOutputStream out = new DigestOutputStream(observedOut, partHash);
				) {
					int read = 0;
					while (this.buffer.getRemaining() > 0 && (read = in.read(buffer, 0, (int)Math.min(this.buffer.getRemaining(), buffer.length))) >= 0) {
						out.write(buffer, 0, read);
					}
				}
				
				currentPosition += this.buffer.getLength();
				
				// If we read zero bytes, we reached the end of the stream.  Break.
				if (this.buffer.getLength() == 0) {
					break;
				}
				
				// Report the Tree Hash of this chunk
				byte[] byteChecksum = partHash.digest();
				String checksum = BinaryUtils.toHex(byteChecksum);
				this.observer.computedTreeHash(partId, byteChecksum);

				// Try to upload this chunk
				int attempts = 0;
				do {
					try (
							InputStream bufferIn = this.buffer.getInputStream();
							
							// KLUDGE: Throttling really belongs closer to EntitySerializer.serialize(), but there
							// wasn't an easy hook for it.  Throttling on input would work well enough, but
							// client.uploadMultipartPart() calculates a SHA-256 checksum on the request before it
							// sends it, then calls reset() on the stream.  Because we know this, don't throttle until
							// reset() has been called at least once.
							InputStream throttledIn = this.throttlingStrategy == null ? bufferIn : new ThrottledInputStream(bufferIn, this.throttlingStrategy) {
								private long resets = 0;
								
								@Override
								public void setBytesPerSecond() {
									if (this.resets > 0) {
										super.setBytesPerSecond();
									}
								}
								
								@Override
								protected long getMaxRead(long currentTime) {
									return this.resets > 0 ? super.getMaxRead(currentTime) : Long.MAX_VALUE;
								}
								
								@Override
								public synchronized void reset() throws IOException {
									super.reset();
									this.resets++;
								}
							};
							
							InputStream observedIn = new ObservedInputStream(throttledIn, new UploadObserver(this.observer, partId));
					) {

						UploadMultipartPartRequest partRequest = new UploadMultipartPartRequest().
								withVaultName(vaultName).
								withBody(observedIn).
								withChecksum(checksum).
								withRange(String.format("bytes %d-%d/*", currentPosition - this.buffer.getLength(), currentPosition - 1)).
								withUploadId(uploadId).
								withAccountId("-");

						UploadMultipartPartResult partResult = client.uploadMultipartPart(partRequest);

						if (!Arrays.equals(BinaryUtils.fromHex(partResult.getChecksum()), byteChecksum)) {
							throw new AmazonClientException("Checksum mismatch");
						}

						break;
					} catch (AmazonClientException e) {
						attempts++;
						observer.exceptionUploadingPart(partId, e, attempts, attempts < this.maxRetries);

						if (attempts >= this.maxRetries) {
							throw new IOException("Failed to upload after " + attempts + " attempts", e);
						}
					} catch (IOException e) {
						attempts++;
						observer.exceptionUploadingPart(partId, e, attempts, attempts < this.maxRetries);

						if (attempts >= this.maxRetries) {
							throw new IOException("Failed to upload after " + attempts + " attempts", e);
						}
					}

					try {
						long sleepingFor = 1000 * (attempts < 15 ? (long)Math.pow(1.5, attempts) : 300);
						this.observer.sleepingBeforeRetry(sleepingFor);
						Thread.sleep(sleepingFor);
					} catch (InterruptedException e) {
						throw new IOException("Upload interrupted", e);
					}
				} while (true);

				partId++;
			} while (this.buffer.getRemaining() == 0);

			byte[] complateHash = completeHash.digest();

			CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest().
					withVaultName(vaultName).
					withUploadId(uploadId).
					withChecksum(BinaryUtils.toHex(complateHash)).
					withArchiveSize(Long.toString(currentPosition));

			CompleteMultipartUploadResult compResult = client.completeMultipartUpload(compRequest);
			String location = compResult.getLocation();

			this.observer.done(complateHash, location);
			return location;

		} catch (IOException e) {
			this.observer.fatalException(e);
			throw e;
		} catch (AmazonClientException e) {
			this.observer.fatalException(e);
			throw e;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 not available", e);
		}
	}

	private static boolean isPowerOfTwo(long val) {
		val--;

		while (val > 0) {
			if ((val & 1) != 1) {
				return false;
			}
			val >>= 1;
		}

		return true;
	}

	protected static class AbstractObserverAdapter {

		protected final GlacierPipeObserver observer;
		protected final int partId;

		protected AbstractObserverAdapter(GlacierPipeObserver observer, int partId) {
			this.observer = Objects.requireNonNull(observer);
			this.partId = partId;
		}

	}

	protected static class BufferingObserver extends AbstractObserverAdapter implements OutputStreamObserver {

		protected BufferingObserver(GlacierPipeObserver observer, int partId) {
			super(observer, partId);
		}

		@Override
		public void streamOpened() {
			this.observer.startBuffering(this.partId);
		}

		@Override
		public void streamClosed(boolean hadException) {
			this.observer.endBuffering(this.partId);
		}

		@Override
		public void bytesWritten(int bytes) {
			this.observer.buffering(this.partId, bytes);
		}
	}

	protected static class UploadObserver extends AbstractObserverAdapter implements InputStreamObserver {

		protected UploadObserver(GlacierPipeObserver observer, int partId) {
			super(observer, partId);
		}

		@Override
		public void streamOpened() {
			this.observer.startPartUpload(this.partId);
		}

		@Override
		public void bytesRead(long bytes) {
			this.observer.partUploading(this.partId, bytes);
		}

		@Override
		public void bytesSkipped(long skipped) { }

		@Override
		public void streamClosed(boolean hadException) {
			this.observer.endPartUpload(this.partId);
		}
	}
}
