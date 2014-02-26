package glacierpipe;

import glacierpipe.io.ThrottledInputStream.ThrottlingStrategy;
import glacierpipe.net.FixedThrottlingStrategy;
import glacierpipe.net.QOSThrottlingStrategy;

import java.io.Closeable;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

public class ProxyingThrottlingStrategy implements ThrottlingStrategy, PropertiesObserver, AutoCloseable {

	protected ThrottlingStrategy throttlingStrategy = null;
	protected Config currentConfig;
	
	public ProxyingThrottlingStrategy(Config config) {
		this.currentConfig = Objects.requireNonNull(config, "configuration was null");
		
		try {
			this.setThrottlingStrategy(config);
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to create ThrottlingStrategy", e);
		}
	}
	
	@Override
	public synchronized void close() throws Exception {
		try {
			if (this.throttlingStrategy instanceof AutoCloseable) {
				((AutoCloseable)this.throttlingStrategy).close();
			} else if (this.throttlingStrategy instanceof Closeable) {
				((Closeable)this.throttlingStrategy).close();
			}
		} finally {
			this.throttlingStrategy = null;
		}
	}

	@Override
	public synchronized double getBytesPerSecond() {
		if (this.throttlingStrategy == null) {
			return Double.POSITIVE_INFINITY;
		} else {
			return this.throttlingStrategy.getBytesPerSecond();
		}
	}

	@Override
	public synchronized void propertiesUpdated(Properties properties) {
		
		ConfigBuilder configurationBuilder = new ConfigBuilder();
		configurationBuilder.setFromConfiguration(this.currentConfig);

		try {
			configurationBuilder.setFromProperties(properties);
			Config newConfig = new Config(configurationBuilder);
			
			if (this.currentConfig.useQOS != newConfig.useQOS) {
				this.setThrottlingStrategy(newConfig);
			} else if (this.currentConfig.useQOS == false && 
					this.currentConfig.maxUploadRate != newConfig.maxUploadRate) {
				this.setThrottlingStrategy(newConfig);
			}
			
			this.currentConfig = newConfig;
			
			// TODO: successful reload
		}
		// Properties parser error
		catch (IllegalArgumentException e) {
			// TODO:
		}
		// Close failed
		catch (Exception e) {
			// TODO:
			e.printStackTrace(System.err);
		}
	}
	
	protected synchronized void setThrottlingStrategy(Config config) throws Exception {
		try {
			this.close();
		} finally {
			if (config.useQOS) {
				URL qosURL = new URL(config.endpoint.replaceFirst("(?i)(?<=^http)s(?=:)", ""));
				this.throttlingStrategy = new QOSThrottlingStrategy(qosURL);
			} else if (!Double.isNaN(config.maxUploadRate)) {
				this.throttlingStrategy = new FixedThrottlingStrategy(config.maxUploadRate);
			} else {
				this.throttlingStrategy = new FixedThrottlingStrategy(Double.NaN);
			}
		}
	}
}
