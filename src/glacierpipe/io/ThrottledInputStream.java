package glacierpipe.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class ThrottledInputStream extends FilterInputStream {

	private long budget;
	private long usage = 0;
	
	private long lastUsed = 0;
	private int quantaPerSecond = 20;
	
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
			this.budget = Math.max(0, Math.round(bytesPerSecond / quantaPerSecond));
		}
	}
	
	public void setBytesPerSecond() {
		double bytesPerSecond = throttlingStrategy.getBytesPerSecond();
		if (bytesPerSecond <= 0 || Double.isInfinite(bytesPerSecond) || Double.isNaN(bytesPerSecond)) {
			this.budget = Long.MAX_VALUE;
		} else {
			this.budget = Math.max(0, Math.round(bytesPerSecond / quantaPerSecond));
		}
	}
	
	@Override
	public int available() throws IOException {
		return Math.min(super.available(), (int)Math.min(getMaxRead(System.currentTimeMillis()), Integer.MAX_VALUE));
	}

	@Override
	public int read() throws IOException {
		while (true) {
			long currentTime = System.currentTimeMillis();
			
			if (isNewQuantum(currentTime) && this.throttlingStrategy != null) {
				this.setBytesPerSecond();
			}
			
			if (getMaxRead(currentTime) > 0) {
				int r = super.read();
				if (r >= 0) {
					markUsed(currentTime, 1);
				}
				return r;
			} else {
				sleepUntilNextChunk(currentTime);
			}
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		while (true) {
			long currentTime = System.currentTimeMillis();
			
			if (isNewQuantum(currentTime) && this.throttlingStrategy != null) {
				this.setBytesPerSecond();
			}
			
			int maxRead = (int)Math.min(getMaxRead(currentTime), Integer.MAX_VALUE);
			if (maxRead > 0 || len == 0) {
				int r = super.read(b, off, Math.min(len, maxRead));
				if (r >= 0) {
					markUsed(currentTime, r);
				}
				return r;
			} else {
				sleepUntilNextChunk(currentTime);
			}
		}
	}

	protected boolean isNewQuantum(long currentTime) {
		int lastQuantum = ((int)(this.lastUsed % 1000) * quantaPerSecond) / 1000;
		int currentQuantum = ((int)(currentTime % 1000) * quantaPerSecond) / 1000;
		
		return (lastQuantum != currentQuantum || currentTime - this.lastUsed >= 1000);
	}
	
	protected long getMaxRead(long currentTime) {
		if (!isNewQuantum(currentTime)) {
			return budget - usage;
		} else {
			return budget;
		}
	}
	
	protected void markUsed(long currentTime, long used) {
		if (isNewQuantum(currentTime)) {
			this.usage = 0;
		}

		this.usage += used;
		
		this.lastUsed = currentTime;
	}
	
	protected void sleepUntilNextChunk(long currentTime) throws IOException {
		long positionInQuantum = currentTime % 1000;
		int currentQuantum = ((int)positionInQuantum * quantaPerSecond) / 1000;
		long nextQuantumStart = ((currentQuantum + 1) * 1000) / quantaPerSecond;
		
		try {
			Thread.sleep(nextQuantumStart - positionInQuantum);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	public interface ThrottlingStrategy {
		
		double getBytesPerSecond();
		
	}
}
