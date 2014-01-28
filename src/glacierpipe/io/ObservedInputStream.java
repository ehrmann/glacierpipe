/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 * 
 */

package glacierpipe.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ObservedInputStream extends FilterInputStream {

	protected final InputStreamObserver observer;
	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected long lastUpdate;
	protected int accumlatedBytes;
	protected long markPosition = 0;
	protected long position = 0;
	protected boolean closed = false;
	protected boolean hadException = false;
	
	public ObservedInputStream(InputStream in, InputStreamObserver observer) {
		super(in);
		this.observer = Objects.requireNonNull(observer, "observer was null");
		this.lastUpdate = System.currentTimeMillis();
		this.observer.streamOpened();
		this.executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				ObservedInputStream.this.bytesRead(0);
			}
		}, 500, 500, TimeUnit.MILLISECONDS);	
	}
	
	@Override
	public void close() throws IOException {
		try {
			try {
				if (!this.closed) {
					this.closed = true;

					int observe = 0;
					
					synchronized (this) {
						observe = this.accumlatedBytes;
						this.accumlatedBytes = 0;
					}
					
					if (observe > 0) {
						this.observer.bytesRead(observe);
					}
					this.observer.streamClosed(this.hadException);
				}
			} finally {
				super.close();
			}
		} finally {
			this.executor.shutdownNow();
		}
	}

	@Override
	public synchronized void mark(int readlimit) {
		super.mark(readlimit);
		this.markPosition = this.position;
	}

	@Override
	public int read() throws IOException {
		try {
			int read = super.read();
			if (read >= 0) {
				this.position++;
				this.bytesRead(1);
			} else {
				this.executor.shutdownNow();
			}
			return read;
		} catch (Throwable t) {
			this.hadException = true;
			throw t;
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		try {
			int read = super.read(b, off, len);
			if (read >= 0) {
				this.position += read;
				this.bytesRead(read);
			} else {
				this.executor.shutdownNow();
			}
			return read;
		} catch (Throwable t) {
			this.hadException = true;
			throw t;
		}
	}

	@Override
	public void reset() throws IOException {
		try {
			super.reset();
			long bytesReset = this.position - this.markPosition;
			this.position = this.markPosition;
			
			synchronized (this) {
				if (this.accumlatedBytes <= bytesReset) {
					bytesReset -= this.accumlatedBytes;
					this.accumlatedBytes = 0;
				} else {
					this.accumlatedBytes -= bytesReset;
					bytesReset = 0;
				}
			}
			
			observer.bytesRead(-bytesReset);
		} catch (Throwable t) {
			this.hadException = true;
			throw t;
		}
	}

	@Override
	public long skip(long n) throws IOException {
		try {
			long skipped = super.skip(n);
			this.observer.bytesSkipped(skipped);
			return skipped;
		} catch (Throwable t) {
			this.hadException = true;
			throw t;
		}
	}

	protected void bytesRead(int bytes) {
		long now = System.currentTimeMillis();
		
		int[] observe = null;
		
		synchronized (this) {
			if (bytes + this.accumlatedBytes < this.accumlatedBytes) {
				observe = new int[] { this.accumlatedBytes, bytes };
				this.accumlatedBytes = 0;
				this.lastUpdate = now;
			} else if (now - this.lastUpdate > 100) {
				observe = new int[] { this.accumlatedBytes + bytes };
				this.accumlatedBytes = 0;
				this.lastUpdate = now;
			} else {
				this.accumlatedBytes += bytes;
			}
		}
		
		if (observe != null) {
			for (int o : observe) {
				observer.bytesRead(o);
			}
		}
	}
}
