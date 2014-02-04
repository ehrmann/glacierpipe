package glacierpipe.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ThrottledInputStream extends FilterInputStream {

	protected long budget;
	
	long lastUsed = 0;
	long[] usage = new long[10];
	protected final ThrottlingStrategy throttlingStrategy;
	
	public ThrottledInputStream(InputStream in, double bytesPerSecond) {
		super(in);
		this.setBytesPerSecond(bytesPerSecond);
		this.throttlingStrategy = null;
	}
	
	public ThrottledInputStream(InputStream in, ThrottlingStrategy throttlingStrategy) {
		super(in);
		this.throttlingStrategy = Objects.requireNonNull(throttlingStrategy, "throttlingStrategy was null");
		this.setBytesPerSecond();
	}

	public void setBytesPerSecond(double bytesPerSecond) {
		if (this.throttlingStrategy != null) {
			throw new IllegalStateException("setBytesPerSecond called when a ThrottlingStrategy was provided");
		} else if (bytesPerSecond <= 0) {
			throw new IllegalArgumentException("bytesPerSecond was negative");
		} else if (Double.isNaN(bytesPerSecond)) {
			throw new IllegalArgumentException("bytesPerSecond was NaN");
		} else if (Double.isInfinite(bytesPerSecond)) {
			this.budget = Long.MAX_VALUE;
		} else {
			this.budget = Math.max(0, Math.round(bytesPerSecond / usage.length));
		}
	}
	
	public void setBytesPerSecond() {
		double bytesPerSecond = throttlingStrategy.getBytesPerSecond();
		if (bytesPerSecond <= 0 || Double.isInfinite(bytesPerSecond) || Double.isNaN(bytesPerSecond)) {
			this.budget = Long.MAX_VALUE;
		} else {
			this.budget = Math.max(0, Math.round(bytesPerSecond / usage.length));
		}
	}
	
	@Override
	public int read() throws IOException {
		while (true) {
			long now = System.currentTimeMillis();
			if (getMaxRead(now) > 0) {
				int r = super.read();
				if (r >= 0) {
					markUsed(now, 1);
				}
				return r;
			} else {
				if (this.throttlingStrategy != null) {
					this.setBytesPerSecond();
				}
				
				sleepUntilNextChunk(now);
			}
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while (true) {
			long now = System.currentTimeMillis();
			int maxRead = (int)Math.min(getMaxRead(now), Integer.MAX_VALUE);
			if (maxRead > 0) {
				int r = super.read(b, off, Math.min(len, maxRead));
				if (r >= 0) {
					markUsed(now, r);
				}
				return r;
			} else {
				if (this.throttlingStrategy != null) {
					this.setBytesPerSecond();
				}
				
				sleepUntilNextChunk(now);
			}
		}
	}

	protected long getMaxRead(long currentTime) {
		int lastIndex = ((int)(this.lastUsed % 1000) * usage.length) / 1000;
		int index = ((int)(currentTime % 1000) * usage.length) / 1000;
		
		if (lastIndex == index) {
			return budget - usage[index];
		} else {
			return budget;
		}
	}
	
	protected void markUsed(long currentTime, long used) {
		int lastIndex = ((int)(this.lastUsed % 1000) * usage.length) / 1000;
		int index = ((int)(currentTime % 1000) * usage.length) / 1000;
		
		if (lastIndex != index) {
			usage[index] = 0;
		}

		usage[index] += used;
		
		this.lastUsed = currentTime;
	}
	
	protected void sleepUntilNextChunk(long currentTime) throws IOException {
		long thisChunk = currentTime % 1000;
		int index = ((int)thisChunk * usage.length) / 1000;
		long nextChunkStart = ((index + 1) * 1000) / usage.length;
		
		try {
			Thread.sleep(nextChunkStart - thisChunk);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	public interface ThrottlingStrategy {
		
		double getBytesPerSecond();
		
	}
}
