package glacierpipe;

import glacierpipe.io.IOBuffer;
import glacierpipe.io.InputStreamObserver;
import glacierpipe.io.ObservedInputStream;
import glacierpipe.io.ObservedOutputStream;
import glacierpipe.io.OutputStreamObserver;

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

	final int maxRetries = 15;

	final long partSize;
	final IOBuffer buffer;
	protected final GlacierPipeObserver observer;

	public GlacierPipe(IOBuffer buffer, GlacierPipeObserver observer) {
		long partSize = buffer.getCapacity();

		if (partSize < 0) {
			throw new IllegalArgumentException("partSize too small");
		} else if (partSize > 1024L * 1024L * 1024L * 4L) {
			throw new IllegalArgumentException("partSize larger than 4GB");
		} else if (!isPowerOfTwo(partSize) || partSize % (1024 * 1024) != 0) {
			throw new IllegalArgumentException("partSize not 1MB * 2^n");
		}

		this.partSize = partSize;
		this.buffer = buffer;
		this.observer = observer;
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
							InputStream observedIn = new ObservedInputStream(bufferIn, new UploadObserver(this.observer, partId));
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