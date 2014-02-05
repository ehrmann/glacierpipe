package glacierpipe.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

public class QOSThrottlingStrategy implements ThrottlingStrategy, AutoCloseable {

	protected final int historyLength;
	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected final URL url;
	
	protected double rate = 2048.0;
	
	protected final AtomicReference<Stats> stats = new AtomicReference<QOSThrottlingStrategy.Stats>(new Stats(0, Double.MAX_VALUE, 0));

	protected Stats baselineStats = null;
	
	
	public QOSThrottlingStrategy(URL url) {
		this.url = Objects.requireNonNull(url, "url was null");
		executor.scheduleWithFixedDelay(new Worker(), 0, 800, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close() throws Exception {
		this.executor.shutdownNow();
	}

	@Override
	public double getBytesPerSecond() {
		Stats stats = this.stats.getAndSet(null);
		if (stats != null) {
			if (this.baselineStats == null) {
				if (stats.samples == historyLength) {
					this.baselineStats = stats;
				}
			} else {
				if and last 5 all in 3 stddevs, increase
				if last 5 all outside 2 stddevs, decrase
				
				
			}
		}

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
}
