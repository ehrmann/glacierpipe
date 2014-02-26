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

import glacierpipe.io.IOBuffer;
import glacierpipe.io.MemoryIOBuffer;
import glacierpipe.terminal.TerminalGlacierPipeObserver;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

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
		
		OPTIONS.addOption(null, "reload-properties", false, "reload properties file on change, possibly changing the current configuration");
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
			
			// Turn the CommandLine into Properties
			Properties cliProperties = new Properties();
			for (Iterator<?> i = cmd.iterator(); i.hasNext(); ) {
				Option o = (Option)i.next();
				
				String opt = o.getLongOpt();
				opt = opt != null ? opt : o.getOpt();
				
				String value = o.getValue();
				value = value != null ? value : "";
				
				cliProperties.setProperty(opt, value);
			}
			
			// Build up a configuration
			ConfigBuilder configBuilder = new ConfigBuilder();
			
			// Archive name
			List<?> archiveList = cmd.getArgList();
			if (archiveList.size() > 1) {
				throw new ParseException("Too many arguments");
			} else if (archiveList.isEmpty()) {
				throw new ParseException("No archive name provided");
			}			
			
			configBuilder.setArchive(archiveList.get(0).toString());
			
			// All other arguments on the command line
			configBuilder.setFromProperties(cliProperties);
			
			// Load any config from the properties file
			Properties fileProperties = new Properties();
			try (InputStream in = new FileInputStream(configBuilder.propertiesFile)) {
				fileProperties.load(in);
			} catch (IOException e) {
				System.err.printf("Warning: unable to read properties file %s; %s%n", configBuilder.propertiesFile, e);
			}
			
			configBuilder.setFromProperties(fileProperties);
			
			// ...
			Config config = new Config(configBuilder);

			IOBuffer buffer = new MemoryIOBuffer(config.partSize);			
			
			AmazonGlacierClient client = new AmazonGlacierClient(new BasicAWSCredentials(config.accessKey, config.secretKey));
			client.setEndpoint(config.endpoint);

			// Actual upload
			try (
					InputStream in = new BufferedInputStream(System.in, 4096);
					PrintWriter writer = new PrintWriter(System.err);
					ObservableProperties configMonitor = config.reloadProperties ? new ObservableProperties(config.propertiesFile) : null;
					ProxyingThrottlingStrategy throttlingStrategy = new ProxyingThrottlingStrategy(config);
			) {
				TerminalGlacierPipeObserver observer = new TerminalGlacierPipeObserver(writer);
				
				if (configMonitor != null) {
					configMonitor.registerObserver(throttlingStrategy);
				}
					
				GlacierPipe pipe = new GlacierPipe(buffer, observer, config.maxRetries, throttlingStrategy);				
				pipe.pipe(client, config.vault, config.archive, in);
			} catch (Exception e) {
				e.printStackTrace(System.err);
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
		for (Entry<String, String> entry : ConfigBuilder.GLACIER_ENDPOINTS.entrySet()) {
			writer.printf("  %20s %s%n", entry.getKey() + " ->", entry.getValue());
		}
		
		writer.flush();
	}
}
