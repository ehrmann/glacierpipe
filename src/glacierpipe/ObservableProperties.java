package glacierpipe;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservableProperties implements AutoCloseable {

	protected static final Logger LOGGER = LoggerFactory.getLogger(ObservableProperties.class);
	
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	
	protected final File file;
	protected long lastModified;
	protected final Set<PropertiesObserver> observers = new LinkedHashSet<>();
	
	public ObservableProperties(File file) {
		this.file = Objects.requireNonNull(file, "file was null");
		this.lastModified = this.file.lastModified();
		executor.scheduleWithFixedDelay(new Worker(), 0, 1, TimeUnit.SECONDS);
	}
	
	public void registerObserver(PropertiesObserver observer) {
		synchronized (this.observers) {
			this.observers.add(observer);
		}
	}
	
	public void unregisterObserver(PropertiesObserver observer) {
		synchronized (this.observers) {
			this.observers.remove(observer);
		}
	}
	
	@Override
	public void close() {
		executor.shutdownNow();
	}

	protected class Worker implements Runnable {

		@Override
		public void run() {
			long lastModified = ObservableProperties.this.file.lastModified();
			if (ObservableProperties.this.lastModified != lastModified) {
				try {
					Properties properties = new Properties();
					
					try (InputStream in = new FileInputStream(ObservableProperties.this.file)) {
						properties.load(in);
					} catch (IOException e) {
						ObservableProperties.LOGGER.error("Failed to read config file", e);
						return;
					}
					
					synchronized (ObservableProperties.this.observers) {
						for (PropertiesObserver observer : ObservableProperties.this.observers) {
							observer.propertiesUpdated(properties);
						}
					}
				} finally {
					ObservableProperties.this.lastModified = lastModified;
				}
			}
		}

	}
}
