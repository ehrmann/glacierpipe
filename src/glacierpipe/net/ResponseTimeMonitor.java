package glacierpipe.net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class ResponseTimeMonitor {

	protected URL url;
	
	public ResponseTimeMonitor(URL url) {
		this.url = Objects.requireNonNull(url, "url was null");
	}
	
	
	
	protected static class Worker implements Runnable {

		protected final URL url;
		
		public Worker(URL url) {
			this.url = Objects.requireNonNull(url, "url was null");
		}
		
		@Override
		public void run() {
			try {
				long time = System.nanoTime();
				HttpURLConnection connection = (HttpURLConnection)this.url.openConnection();
				connection.connect();
				try {
					connection.getResponseCode();
					time = System.nanoTime() - time;
				} finally {
					connection.disconnect();
				}
				
				time /= 1000000;
				
				
			} catch (IOException e) {
				
			}
		}
		
	}
}
