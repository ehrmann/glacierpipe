package glacier_pipe.terminal;

import glacier_pipe.GlacierPipeObserver;

import java.io.PrintWriter;
import java.io.Writer;

public class TerminalGlacierPipeObserver implements GlacierPipeObserver {

	protected final PrintWriter writer;
	
	protected long startTime = System.currentTimeMillis();
	protected long totalBytes = 0;
	
	protected ProgressPrinter progressPrinter;
	protected long bytesInPart = 0;
	
	public TerminalGlacierPipeObserver(Writer writer) {
		this.writer = new PrintWriter(writer, true);
	}
	
	@Override
	public void gotUploadId(String uploadId) {
		writer.printf("Upload ID: %s%n", uploadId);
	}

	@Override
	public void startBuffering(int partId) {
		bytesInPart = 0;
		
		writer.printf("Part %d, %s - ?%n", partId, Util.toHumanReadableDataSize(this.totalBytes));
		writer.println("  Buffering...");
		
		progressPrinter = new ProgressPrinter(-1);
		writer.print("  ");
		progressPrinter.print(writer);
	}

	@Override
	public void buffering(int partId, long dataRead) {
		bytesInPart += dataRead;
		
		writer.print('\r');
		writer.print("  ");
		progressPrinter.addCurrent(dataRead);
		progressPrinter.print(writer);
		writer.flush();
	}

	@Override
	public void endBuffering(int partId) {
		writer.print('\r');
		writer.print("  ");
		progressPrinter.done();
		progressPrinter.print(writer);
		writer.println();
		writer.println();
		
		totalBytes += bytesInPart;
	}

	@Override
	public void computedTreeHash(int partId, byte[] treeHash) {
		writer.print("  Tree Hash: 0x");
		Util.printHex(writer, treeHash);
		writer.println();
		writer.println();
	}

	@Override
	public void startPartUpload(int partId) {
		writer.println("  Uploading...");
		
		progressPrinter = new ProgressPrinter(bytesInPart);
		writer.print("  ");
		progressPrinter.print(writer);
	}

	@Override
	public void partUploading(int partId, long dataUploaded) {
		writer.print('\r');
		writer.print("  ");
		progressPrinter.addCurrent(dataUploaded);
		progressPrinter.print(writer);
		writer.flush();
	}

	@Override
	public void endPartUpload(int partId) {
		writer.print('\r');
		writer.print("  ");
		progressPrinter.done();
		progressPrinter.print(writer);
		writer.println();
		writer.println();
	}

	@Override
	public void exceptionUploadingPart(int partId, Exception e, int attempt, boolean retrying) {
		writer.println();
		writer.printf("  Error uploading: %s%n", e.getMessage());
		writer.printf("  Attempt %d.  %s...%n", attempt + 1, retrying ? "Retrying" : "Aborting");
	}

	@Override
	public void sleepingBeforeRetry(long sleepingFor) {
		writer.print("  Sleeping for ");
		Util.printTime(writer, sleepingFor, false);
		writer.println("...");
	}

	@Override
	public void done(byte[] finalTreeHash, String location) {
		writer.print("Done.");
		
		writer.print("  Uploaded ");
		writer.print(Util.toHumanReadableDataSize(totalBytes));
		writer.print(" in ");
		Util.printTime(writer, System.currentTimeMillis() - startTime, false);
		
		// FIXME: this said 0 B/s
		writer.print(" (");
		writer.print(Util.toHumanReadableDataSize(totalBytes / (System.currentTimeMillis() - startTime) / 1000));
		writer.println(")/s");
		
		writer.print("  Tree Hash: 0x");
		Util.printHex(writer, finalTreeHash);
		writer.println();
		
		writer.printf("  Location: %s%n", location);
	}

	@Override
	public void fatalException(Exception e) {
		writer.println();
		writer.printf("Fatal Exception: %s%n", e.getMessage());
		writer.println("Aborting.");
	}

}
