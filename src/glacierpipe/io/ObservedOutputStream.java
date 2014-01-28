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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ObservedOutputStream extends FilterOutputStream {

	protected final OutputStreamObserver observer;
	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected long lastUpdate;
	protected int accumlatedBytes;
	protected boolean closed = false;
	
	public ObservedOutputStream(OutputStream out, OutputStreamObserver observer) {
		super(out);
		this.observer = Objects.requireNonNull(observer, "observer was null");
		this.lastUpdate = System.currentTimeMillis();
		this.observer.streamOpened();
		this.executor.schedule(new Runnable() {
			@Override
			public void run() {
				ObservedOutputStream.this.bytesWritten(0);
			}
		}, 500, TimeUnit.MILLISECONDS);
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
						this.observer.bytesWritten(observe);
					}
					// FIXME:
					this.observer.streamClosed(false);
				}
			} finally {
				super.close();
			}
		} finally {
			this.executor.shutdownNow();
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		super.out.write(b, off, len);
		this.bytesWritten(len);
	}

	@Override
	public void write(int b) throws IOException {
		super.out.write(b);
		this.bytesWritten(1);
	}

	protected void bytesWritten(int bytes) {
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
				observer.bytesWritten(o);
			}
		}
	}
}
