package glacierpipe.terminal;

import glacierpipe.terminal.TerminalGlacierPipeObserver;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import org.junit.Test;

public class TerminalGlacierPipeObserverTest {

	@Test
	public void test() {
		
		Random random = new Random(42);
		
		TerminalGlacierPipeObserver o = new TerminalGlacierPipeObserver(new PrintWriter(System.out, true));
		
		o.gotUploadId("42");
		
		for (int part = 0; part < 4; part++) {
			// Buffering
			o.startBuffering(part);

			for (int block = 0; block < 64; block++) {
				o.buffering(part, 4096);
			}
			
			o.endBuffering(part);
			
			// Hashing			
			byte[] hash = new byte[32];
			random.nextBytes(hash);
			o.computedTreeHash(part, hash);
			
			// Uploading
			boolean retry;
			int attempts = 0;
			outer: do {
				retry = false;
				attempts++;
				
				o.startPartUpload(part);
				
				for (int block = 0; block < 64; block++) {
					if (block == 54 && part == 2 && attempts == 1) {
						o.exceptionUploadingPart(part, new IOException("Test Exception"), 0, true);
						o.sleepingBeforeRetry(3000);
						retry = true;
						continue outer;
					}
					o.partUploading(part, 4096);
				}
				
				o.endPartUpload(part);
			} while (retry);
		}
		
		byte[] hash = new byte[32];
		random.nextBytes(hash);
		o.done(hash, "/vault/object");
	}
	
}
