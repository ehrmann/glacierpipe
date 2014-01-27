package glacier_pipe;

import glacier_pipe.io.IOBuffer;
import glacier_pipe.io.MemoryIOBuffer;
import glacier_pipe.terminal.TerminalGlacierPipeObserver;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.glacier.AmazonGlacierClient;

public class GlacierPipeMain {

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
	
	public static final Options OPTIONS = new Options();
	static {
		OptionGroup action = new OptionGroup();
		
		action.addOption(new Option("u", "upload", false, "upload stdin to glacier"));
		action.addOption(new Option(null, "help", false, "show help"));
		
		OPTIONS.addOptionGroup(action);

		OPTIONS.addOption("e", "endpoint", true, "URL of the amazon AWS endpoint where your vault is");
		OPTIONS.addOption("v", "vault", true, "Name of your vault");
				
		OptionBuilder.withArgName("p");
		OptionBuilder.withLongOpt("partsize");
		OptionBuilder.withType(Long.class);
		OptionBuilder.withDescription("sets the size of each part for multipart uploads (default: 16M)");
		OptionBuilder.hasArg();
		OPTIONS.addOption(OptionBuilder.create());

		OPTIONS.addOption(null, "credentials", true, "path to your aws credentials file (default: $HOME/aws.properties)");
	}
	
	public static void main(String[] args) throws IOException, ParseException {
		CommandLineParser parser = new GnuParser();

		CommandLine cmd = parser.parse(OPTIONS, args);
		
		if (cmd.hasOption("help")) {
			
		} else if (cmd.hasOption("upload")) {
			
			String endpoint = cmd.getOptionValue("endpoint");
			if (GLACIER_ENDPOINTS.containsKey(endpoint)) {
				endpoint = GLACIER_ENDPOINTS.get(endpoint);
			}
			
			long partSize = (Long)cmd.getParsedOptionValue("partsize");
			IOBuffer buffer = new MemoryIOBuffer(partSize);
			
			String vault = (String)cmd.getParsedOptionValue("vault");
			
			// Credentials
			String credentialsFile = cmd.getOptionValue("credentials", System.getProperty("user.home") + "/aws.properties");
			Properties credentialsProperties = new Properties();
			
			try (InputStream in = new FileInputStream(credentialsFile)) {
				credentialsProperties.load(in);
			}
			
			String accessKey = credentialsProperties.getProperty("accessKey");
			String secretKey = credentialsProperties.getProperty("secretKey");
			
			AmazonGlacierClient client = new AmazonGlacierClient(new BasicAWSCredentials(accessKey, secretKey));
			client.setEndpoint(endpoint);
			
			String archive = "";
			
			try (
					InputStream in = new BufferedInputStream(System.in, 4096);
					PrintWriter writer = new PrintWriter(System.err);
			) {
				TerminalGlacierPipeObserver observer = new TerminalGlacierPipeObserver(writer);
				GlacierPipe pipe = new GlacierPipe(buffer, observer);
				pipe.pipe(client, vault, archive, in);
			}
		}
	}
	
}
