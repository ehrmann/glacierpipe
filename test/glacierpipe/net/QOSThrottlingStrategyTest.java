package glacierpipe.net;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Random;

import org.junit.Test;

public class QOSThrottlingStrategyTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
	}
	
	@Test
	public void testQOSThrottlingStrategy() throws InterruptedException {
		Random random = new Random(42);
		
		try (MockQOSThrottlingStrategy qos = new MockQOSThrottlingStrategy()) {
			final long start = System.currentTimeMillis();
			double bps = 0.0;
			
			while (System.currentTimeMillis() - start < 240000) {
				if (bps < 1200000) {
					qos.latency = 50 + random.nextInt(15);
				} else {
					double a = 2.0 / 900000000.0;
					double latency = a * Math.pow(bps - 1200000.0, 2) + 50.0 + random.nextInt(15);
					qos.latency = Math.round(latency);
				}
				
				System.err.printf("%.3f KB/s, %d ms\n", bps / 1024.0, qos.latency);
				
				bps = qos.getBytesPerSecond() ;
				
				Thread.sleep(500);
			}
		}
	}
	
	protected static class MockQOSThrottlingStrategy extends QOSThrottlingStrategy {

		static final URL URL;
		static {
			try {
				URL = new URL("http://glacierpipe.local");
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}
		
		public long latency;
		
		public MockQOSThrottlingStrategy() {
			super(URL);
		}

		@Override
		protected long getLatency() {
			return this.latency;
		}
	}
}
