package glacierpipe.net;

import glacierpipe.net.QOSThrottlingStrategy.Stats;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;

import org.junit.Test;

public class QOSThrottlingStrategyTest {

	static {
		System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
	}
	
	protected static final TreeMap<Long, Stats> ADSL_LATENCY_PROFILE = new TreeMap<Long, Stats>();
	static {
		try (
				InputStream in = QOSThrottlingStrategyTest.class.getResourceAsStream("adsl_latency_profile.csv");
				Scanner scanner = new Scanner(in, "UTF-8");
		) {
			scanner.useDelimiter(Pattern.compile("[,\r\n]"));
			while (scanner.hasNext()) {
				long kilobytesPerSecond = scanner.nextLong();
				double mean = scanner.nextDouble();
				double stddev = scanner.nextDouble();
				
				ADSL_LATENCY_PROFILE.put(kilobytesPerSecond * 1024, new Stats(mean, stddev, 1));
			}
		} catch (IOException e) {
			throw new RuntimeException("Error loading adsl_latency_profile.csv", e);
		}
	}
	
	@Test
	public void testQOSThrottlingStrategy() throws InterruptedException {
		Random random = new Random(42);
		
		try (MockQOSThrottlingStrategy qos = new MockQOSThrottlingStrategy()) {
			final long start = System.currentTimeMillis();
			double bps = 0.0;
			
			while (System.currentTimeMillis() - start < 240000) {
				Entry<Long, Stats> entry = ADSL_LATENCY_PROFILE.floorEntry(Math.round(bps));
				if (entry == null) {
					entry = ADSL_LATENCY_PROFILE.firstEntry();
				}
				
				Stats stats = entry.getValue();
				
				qos.latency = Math.max(0, Math.round(stats.mean + random.nextGaussian() * stats.stddev));

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
