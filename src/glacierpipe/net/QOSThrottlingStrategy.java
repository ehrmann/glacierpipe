package glacierpipe.net;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QOSThrottlingStrategy implements ThrottlingStrategy, AutoCloseable {

	protected static final Logger LOGGER = LoggerFactory.getLogger(QOSThrottlingStrategy.class);
	
	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected final URL url;
	
	protected final AtomicReference<Stats> stats = new AtomicReference<Stats>();
	protected ScheduledFuture<?> workerFuture = null;
	protected Stats baselineStats = null;
	protected long startedHolding = 0;
	
	protected double rate = 16384.0;
	
	protected State state = States.BASELINING;
	
	public QOSThrottlingStrategy(URL url) {
		this.url = Objects.requireNonNull(url, "url was null");
	}

	@Override
	public void close() {
		this.executor.shutdownNow();
	}

	@Override
	public double getBytesPerSecond() {
		this.state = state.process(this);
		return rate;
	}
	
	protected long getLatency() {
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

			return time / 1000000;
		} catch (IOException e) {
			return -1;
		}
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
			long time = getLatency();
			
			history[(historyStart + historySize) % history.length] = time;
			historySize = Math.min(historySize + 1, history.length);
			if (historySize == history.length) {
				historyStart = (historyStart + 1) % history.length;
			}
			
			double sum = 0.0;
			for (int i = historyStart, count = historySize - 1; count >= 0; i = (i + 1) % history.length, count--) {
				sum += history[i];
			}
			
			double mean = sum / historySize;
			
			double stddev = 0.0;
			
			for (int i = historyStart, count = historySize - 1; count >= 0; i = (i + 1) % history.length, count--) {
				double diff = mean - history[i];
				stddev += (diff * diff) / historySize;
			}
			
			stddev = Math.sqrt(stddev);
			
			stats.set(new Stats(mean, stddev, historySize));
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
			protected static final int HISTORY_LENGTH = 40;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 800, TimeUnit.MILLISECONDS);
					LOGGER.debug("{} baselining", System.identityHashCode(c));
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					c.baselineStats = stats;
					LOGGER.debug("{} baselined.  mean = {}; stddev = {}", System.identityHashCode(c), stats.mean, stats.stddev);
					c.workerFuture.cancel(true);
					c.workerFuture = null;
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
					LOGGER.debug("{} throttling up", System.identityHashCode(c));
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					if (stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.rate *= .9;
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						return HOLDING;
					} else {
						c.rate *= 1.1;
						LOGGER.debug("{} throttling up.  rate {}", System.identityHashCode(c), c.rate);
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
					LOGGER.debug("{} throttling down", System.identityHashCode(c));
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null && stats.samples == HISTORY_LENGTH) {
					if (stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.rate *= .99;
						LOGGER.debug("{} throttling down.  rate {}", System.identityHashCode(c), c.rate);
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
			protected static final long MAX_HOLD_MS = 10 * 60 * 1000;
			
			@Override
			public State process(QOSThrottlingStrategy c) {
				if (c.workerFuture == null) {
					c.workerFuture = c.executor.scheduleWithFixedDelay(c.new Worker(HISTORY_LENGTH), 0, 5000, TimeUnit.MILLISECONDS);
					c.startedHolding = System.currentTimeMillis();
					LOGGER.debug("{} holding at {}", System.identityHashCode(c), c.rate);
				}
				
				Stats stats = c.stats.getAndSet(null);
				if (stats != null) {
					if (stats.samples >= HISTORY_LENGTH / 2 && stats.mean > c.baselineStats.mean + c.baselineStats.stddev) {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						c.startedHolding = 0;
						return THROTTLING_DOWN;
					} else if (stats.samples == HISTORY_LENGTH && stats.mean < c.baselineStats.mean + c.baselineStats.stddev / 2) {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						c.startedHolding = 0;
						return THROTTLING_UP;
					} else if (System.currentTimeMillis() - c.startedHolding > MAX_HOLD_MS) {
						c.workerFuture.cancel(true);
						c.workerFuture = null;
						c.startedHolding = 0;
						c.rate *= 0.5;
						return BASELINING;
					}
				}
				
				return this;
			}
		},
	}
}
