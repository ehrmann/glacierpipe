package glacierpipe.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;

public class QOSThrottlingStrategy implements ThrottlingStrategy, AutoCloseable {

	protected final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	protected final URL url;
	
	protected volatile Stats stats = new Stats(0, Double.MAX_VALUE);
	
	
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
		// TODO Auto-generated method stub
		return 0;
	}
	
	protected class Worker implements Runnable {
		protected final long[] history = new long[64];
		protected int historyStart = 0;
		protected int historySize = 0;
		
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
				
				stats = new Stats(mean, stddev);
			} catch (IOException e) {
				
			}
		}
	}
	
	protected static class Stats {
		public final double mean;
		public final double stddev;
		
		public Stats(double mean, double stddev) {
			this.mean = mean;
			this.stddev = stddev;
		}
	}
}
