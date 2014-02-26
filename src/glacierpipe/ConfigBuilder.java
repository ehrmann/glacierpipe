package glacierpipe;

import glacierpipe.format.StringFormat;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class ConfigBuilder {

	public static final Map<String, String> GLACIER_ENDPOINTS;
	static {
		Map<String, String> glacierEndpoints = new TreeMap<String, String>();

		glacierEndpoints.put("us-east-1", "https://glacier.us-east-1.amazonaws.com/");
		glacierEndpoints.put("us-west-2", "https://glacier.us-west-2.amazonaws.com/");
		glacierEndpoints.put("us-west-1", "https://glacier.us-west-1.amazonaws.com/");
		glacierEndpoints.put("eu-west-1", "https://glacier.eu-west-1.amazonaws.com/");
		glacierEndpoints.put("ap-southeast-2", "https://glacier.ap-southeast-2.amazonaws.com/");
		glacierEndpoints.put("ap-northeast-1", "https://glacier.ap-northeast-1.amazonaws.com/");

		GLACIER_ENDPOINTS = Collections.unmodifiableMap(glacierEndpoints);
	}
	
	public String endpoint;
	
	public long partSize = 1024 * 1024 * 16;
	public int maxRetries = 1000;
	public File propertiesFile = new File(System.getProperty("user.home") + File.separator + "aws.properties");
	
	public double maxUploadRate;
	public boolean useQOS = false;
	public URL qosURL;
	
	public boolean reloadProperties = false;
	
	public String vault;
	public String archive;
	
	public String accessKey;
	public String secretKey;
	
	public ConfigBuilder() {
		
	}
	
	public ConfigBuilder setFromConfiguration(Config configuration) {
		this.endpoint = configuration.endpoint;
		
		this.partSize = configuration.partSize;
		this.maxRetries = configuration.maxRetries;
		this.propertiesFile = configuration.propertiesFile;
		
		this.maxUploadRate = configuration.maxUploadRate;
		this.useQOS = configuration.useQOS;
		this.qosURL = configuration.qosURL;
		
		this.reloadProperties = configuration.reloadProperties;
		
		this.vault = configuration.vault;
		this.archive = configuration.archive;
		
		this.accessKey = configuration.accessKey;
		this.secretKey = configuration.secretKey;
		
		return this;
	}

	public ConfigBuilder setFromProperties(Properties properties) {
		
		// Endpoint
		if (properties.containsKey("endpoint")) {
			String endpoint = properties.getProperty("endpoint");
			if (GLACIER_ENDPOINTS.containsKey(endpoint)) {
				this.endpoint = GLACIER_ENDPOINTS.get(endpoint);
			} else {
				this.endpoint = endpoint;
			}
		}

		// Set up the part size
		if (properties.containsKey("partsize")) {
			try {
				this.partSize = StringFormat.parseBinarySuffixedLong(properties.getProperty("partsize"));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Illegal partsize");
			}
		}
		
		// How many times should we retry the upload?
		if (properties.containsKey("max-retries")) {
			try {
				this.maxRetries = Integer.parseInt(properties.getProperty("max-retries"));
			} catch (NumberFormatException e) {
				throw new NumberFormatException("Failed to parse max-retries: " + properties.getProperty("max-retries"));
			}
		}
		
		// Should we poll the properties file for changes?
		if (properties.containsKey("reload-properties")) {
			String reloadProperties = properties.getProperty("reload-properties");
			reloadProperties = reloadProperties != null ? reloadProperties.trim().toLowerCase() : null;
			
			if (reloadProperties == null || reloadProperties.isEmpty()) {
				this.reloadProperties = true;
			} else if ("yes".equals(reloadProperties) || "1".equals(reloadProperties) || "true".equals(reloadProperties)) {
				this.reloadProperties = true;
			} else if ("no".equals(reloadProperties) || "0".equals(reloadProperties) || "false".equals(reloadProperties)) {
				this.reloadProperties = false;
			} else {
				throw new IllegalArgumentException("Unrecognized valued for reload-properties: " + reloadProperties);
			}
		}
		
		// Vault name
		if (properties.containsKey("vault")) {
			this.vault = properties.getProperty("vault");
		}
		
		// Archive name from properties seems like a bad idea.
		
		// Throttling
		if (properties.containsKey("max-upload-rate")) {
			this.setMaxUploadRate(properties.getProperty("max-upload-rate"));
		}
		
		// Credentials
		// Support for glacieruploader names
		if (properties.containsKey("accessKey")) {
			this.accessKey = properties.getProperty("accessKey");
		}
		if (properties.containsKey("secretKey")) {
			this.secretKey = properties.getProperty("secretKey");
		}
		
		if (properties.containsKey("access-key")) {
			this.accessKey = properties.getProperty("access-key");
		}
		if (properties.containsKey("secret-key")) {
			this.secretKey = properties.getProperty("secret-key");
		}
		
		return this;
	}
	
	public ConfigBuilder setMaxUploadRate(String maxUploadRate) {
		// FIXME: qosURL never gets set
		if ("automatic".equals(maxUploadRate)) {
			this.useQOS = true;
			this.maxUploadRate = Double.NaN;
		} else if (maxUploadRate == null || maxUploadRate.trim().isEmpty()) {
			this.maxUploadRate = Double.NaN;
			this.useQOS = false;
		} else {
			this.maxUploadRate = StringFormat.parseBinarySuffixedDouble(maxUploadRate);
			this.useQOS = false;
		}
		
		return this;
	}

	public ConfigBuilder setArchive(String archive) {
		this.archive = archive;
		return this;
	}
}
