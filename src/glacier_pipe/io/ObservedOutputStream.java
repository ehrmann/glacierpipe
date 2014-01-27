package glacier_pipe.io;

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
