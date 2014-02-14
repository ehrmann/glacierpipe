/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package glacierpipe;

import glacierpipe.format.StringFormat;
import glacierpipe.io.IOBuffer;
import glacierpipe.io.MemoryIOBuffer;
import glacierpipe.net.FixedThrottlingStrategy;
import glacierpipe.net.QOSThrottlingStrategy;
import glacierpipe.terminal.TerminalGlacierPipeObserver;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
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
		
		OptionBuilder.withLongOpt("partsize");
		OptionBuilder.withArgName("bytes");
		OptionBuilder.withDescription("the size of each part for multipart uploads.  Must be a power of 2 between (inclusive) 1MB and 4GB (default: 16MB)");
		OptionBuilder.hasArg();
		OPTIONS.addOption(OptionBuilder.create("p"));
		
		OptionBuilder.withLongOpt("max-retries");
		OptionBuilder.withArgName("count");
		OptionBuilder.withType(Number.class);
		OptionBuilder.withDescription("the maximum number of times to retry uploading a chunk");
		OptionBuilder.hasArg();
		OPTIONS.addOption(OptionBuilder.create("r"));
		
		OptionBuilder.withLongOpt("max-upload-rate");
		OptionBuilder.withArgName("[Bps | automatic]");
		OptionBuilder.withDescription("the maximum upload rate");
		OptionBuilder.hasArg();
		OptionBuilder.isRequired(false);
		OPTIONS.addOption(OptionBuilder.create());

		OPTIONS.addOption(null, "credentials", true, "path to your aws credentials file (default: $HOME/aws.properties)");
	}
	
	public static void main(String[] args) throws IOException, ParseException {
		CommandLineParser parser = new GnuParser();

		CommandLine cmd = parser.parse(OPTIONS, args);
		
		if (cmd.hasOption("help")) {
			try (PrintWriter writer = new PrintWriter(System.err)) {
				printHelp(writer);
			}

			System.exit(0);
		} else if (cmd.hasOption("upload")) {
			
			// Endpoint
			String endpoint = cmd.getOptionValue("endpoint");
			if (GLACIER_ENDPOINTS.containsKey(endpoint)) {
				endpoint = GLACIER_ENDPOINTS.get(endpoint);
			}
			
			// Set up the part size
			long partSize;
			try {
				partSize = StringFormat.parseBinarySuffixedLong((String)cmd.getOptionValue("partsize"));
			} catch (NumberFormatException e) {
				throw new ParseException("Illegal partsize");
			}
			
			IOBuffer buffer = new MemoryIOBuffer(partSize);
			
			// How many times should we retry the upload?
			// At ~5 min between attempts, 1000 works out to 3.5 days
			int maxRetries = 1000;
			if (cmd.getParsedOptionValue("max-retries") != null) {
				maxRetries = ((Number)cmd.getParsedOptionValue("max-retries")).intValue();
			}
			
			// Vault name
			String vault = (String)cmd.getOptionValue("vault");
			
			// Archive name
			List<?> archiveList = cmd.getArgList();
			if (archiveList.size() != 1) {
				throw new ParseException("No archive name provided");
			}
			
			String archive = archiveList.get(0).toString();
			
			// Throttling
			double maxUploadRate = Double.NaN;
			boolean useQOS = false;
			String maxUploadRateStr = (String)cmd.getOptionValue("max-upload-rate");
			if (maxUploadRateStr != null) {
				if ("automatic".equals(maxUploadRateStr)) {
					useQOS = true;
				} else {
					maxUploadRate = StringFormat.parseBinarySuffixedDouble(maxUploadRateStr);
				}
			}

			// Credentials
			String credentialsFile = cmd.getOptionValue("credentials", System.getProperty("user.home") + File.separator + "aws.properties");
			Properties credentialsProperties = new Properties();
			
			try (InputStream in = new FileInputStream(credentialsFile)) {
				credentialsProperties.load(in);
			}
			
			String accessKey = credentialsProperties.getProperty("accessKey");
			String secretKey = credentialsProperties.getProperty("secretKey");
			
			// Set up the client
			AmazonGlacierClient client = new AmazonGlacierClient(new BasicAWSCredentials(accessKey, secretKey));
			client.setEndpoint(endpoint);

			// Actual upload
			try (
					InputStream in = new BufferedInputStream(System.in, 4096);
					PrintWriter writer = new PrintWriter(System.err);
					QOSThrottlingStrategy qosStrategy = useQOS ? new QOSThrottlingStrategy(new URL(endpoint.replaceFirst("(?i)(?<=^http)s(?=:)", ""))) : null;
			) {
				TerminalGlacierPipeObserver observer = new TerminalGlacierPipeObserver(writer);
				
				
				GlacierPipe pipe;
				if (!Double.isNaN(maxUploadRate)) {
					pipe = new GlacierPipe(buffer, observer, maxRetries, new FixedThrottlingStrategy(maxUploadRate));
				} else if (qosStrategy != null) {
					pipe = new GlacierPipe(buffer, observer, maxRetries, qosStrategy);
				} else {
					pipe = new GlacierPipe(buffer, observer, maxRetries);
				}
				
				pipe.pipe(client, vault, archive, in);
			}
			
			System.exit(0);
		} else {
			try (PrintWriter writer = new PrintWriter(System.err)) {
				writer.println("No action specified.");
				printHelp(writer);
			}

			System.exit(-1);
		}
	}
	
	public static void printHelp(PrintWriter writer) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(writer, HelpFormatter.DEFAULT_WIDTH,
				"<other-command> ... | java -jar glacierpipe.jar [--help | --upload] -e <glacier-endpoint> -v <vault-nane> <archive-name>",
				null,
				 OPTIONS,
				 HelpFormatter.DEFAULT_LEFT_PAD,
				 HelpFormatter.DEFAULT_DESC_PAD,
				 null);
		
		writer.printf("%nBuild-in endpoint aliases:%n%n");
		for (Entry<String, String> entry : GLACIER_ENDPOINTS.entrySet()) {
			writer.printf("  %20s %s%n", entry.getKey() + " ->", entry.getValue());
		}
		
		writer.flush();
	}

}
