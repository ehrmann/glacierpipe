package glacierpipe.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

public class QOSThrottlingStrategy implements ThrottlingStrategy, AutoCloseable {

	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected final URL url;
	
	protected final AtomicReference<Stats> stats = new AtomicReference<Stats>();
	protected ScheduledFuture<?> workerFuture = null;
	protected Stats baselineStats = null;
	
	protected double rate = 2048.0;
	
	protected State state = States.BASELINING;
	
	public QOSThrottlingStrategy(URL url) {
		this.url = Objects.requireNonNull(url, "url was null");
	}

	@Override
	public void close() throws Exception {
		this.executor.shutdownNow();
	}

	@Override
	public double getBytesPerSecond() {
		this.state = state.process(this);
		return rate;
	}
	
	protected class Worker implements Runnable {
		protected final long[] history;
		protected int historyStart = 0;
		protected int historySize = 0;
		
		public Worker(int historyLength) {
			history = new long[historyLength];
		}
		
		@Override
		public void run() {
			try {
				long time = System.nanoTime();
				HttpURLConnection connection = (HttpURLConnection)url.openConnection();
				connection.connect();
				try {
					connection.getResponseCode();
					time = System.nanoTime() - time;
				} finally {
					connection.disconnect();
				}
				
				time /= 1000000;
				
				history[(historyStart + historySize) % history.length] = time;
				historySize = Math.min(historySize + 1, history.length);
				
				double sum = 0.0;
				for (int i = historyStart, count = historySize - 1; count >= 0; i = (i + 1) % history.length, count--) {
					sum += history[i];
				}
				
				double mean = sum / historySize;
				
				double stddev = 0.0;
				
				for (int i = historyStart, count = historySize - 1; count >= 0; i = (i + 1) % history.length, count--) {
					double diff = mean - history[i];
					stddev += diff * diff;
				}
				
				stddev = Math.sqrt(stddev);
				
				stats.set(new Stats(mean, stddev, historySize));
			} catch (IOException e) {
				// TODO: handle frequent IOExceptions
			}
		}
	}
	
	protected static class Stats {
		public final double mean;
		public final double stddev;
		public final int samples;
		
		public Stats(double mean, double stddev, int samples) {
			this.mean = mean;
			this.stddev = stddev;
			this.samples = samples;
		}
	}
	
	protected interface State {
		public State process(QOSThrottlingStrategy c);
	}
	
	protected enum States implements State {
		BASELINING() {
			protected static final int HISTORY_LENGTH = 64;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 800, TimeUnit.MILLISECONDS);
					c.rate = 2048;
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					c.baselineStats = stats;
					return THROTTLING_UP;
				}
				
				return this;
			}
		},
		
		THROTTLING_UP() {
			protected static final int HISTORY_LENGTH = 5;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 800, TimeUnit.MILLISECONDS);
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					if (stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.rate *= .9;
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						return HOLDING;
					} else {
						c.rate *= 1.125;
					}
				}
				
				return this;
			}
		},
		
		THROTTLING_DOWN() {
			protected static final int HISTORY_LENGTH = 5;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 800, TimeUnit.MILLISECONDS);
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					if (stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.rate *= .98;
					} else {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						return HOLDING;
					}
				}
				
				return this;
			}
		},
		
		HOLDING() {
			protected static final int HISTORY_LENGTH = 10;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 5000, TimeUnit.MILLISECONDS);
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null) {
					if (stats.samples >= HISTORY_LENGTH / 2 && stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						return THROTTLING_DOWN;
					} else if (stats.samples == HISTORY_LENGTH && stats.mean < c.baselineStats.mean + c.baselineStats.stddev / 2) {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						return THROTTLING_UP;
					}
				}
				
				// TODO: occasionally throttle down (or rebaseline)
				
				return this;
			}
		},
	}
}
