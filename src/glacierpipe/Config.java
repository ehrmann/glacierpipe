package glacierpipe;

import java.io.File;
import java.net.URL;
import java.util.Objects;

public class Config {

	public final String endpoint;
	
	public final long partSize;
	public final int maxRetries;
	public final File propertiesFile;
	public final boolean reloadProperties;
	
	public final double maxUploadRate;
	public final boolean useQOS;
	public final URL qosURL;
	
	public final String vault;
	public final String archive;
	
	public final String accessKey;
	public final String secretKey;
	
	public Config(ConfigBuilder builder) {
		this.endpoint = Objects.requireNonNull(builder.endpoint, "endpoint was null");
		
		if (builder.partSize < 1024L * 1024L || builder.partSize > 1024L * 1024L * 4096L || !isPowerOfTwo(builder.partSize)) {
			throw new IllegalArgumentException("invalid partSize; must be a power of two 1 MB >= n >= 4 GB; partsize = " + builder.partSize);
		}
		this.partSize = builder.partSize;
		
		if (builder.maxRetries < 0) {
			throw new IllegalArgumentException("maxRetries was negative");
		}
		this.maxRetries = builder.maxRetries;
		
		this.propertiesFile = builder.propertiesFile;
		this.reloadProperties = builder.reloadProperties;
		
		if (builder.maxUploadRate <= 0.0) {
			throw new IllegalArgumentException("maxUploadRate <= 0");
		}
		this.maxUploadRate = builder.maxUploadRate;
		
		this.useQOS = builder.useQOS;
		this.qosURL = this.useQOS ? Objects.requireNonNull(builder.qosURL, "using qos, but qos url was null") : null;
		
		this.vault = Objects.requireNonNull(builder.vault, "vault name required");
		this.archive = Objects.requireNonNull(builder.archive, "archive name required");
		
		this.accessKey = Objects.requireNonNull(builder.accessKey, "accessKey required");
		this.secretKey = Objects.requireNonNull(builder.secretKey, "secretKey required");
		
	}
	
	public static boolean isPowerOfTwo(long val) {
		val--;

		while (val > 0) {
			if ((val & 1) != 1) {
				return false;
			}
			val >>= 1;
		}

		return true;
	}

}
