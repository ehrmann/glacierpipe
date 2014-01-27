package glacier_pipe.terminal;

import java.io.PrintWriter;

import org.junit.Test;

public class ProgressPrinterTest {

	@Test
	public void testSpinner() {
		ProgressPrinter printer = new ProgressPrinter(-1);
		
		PrintWriter writer = new PrintWriter(System.out, true);
		long total = 0;
		long start = System.currentTimeMillis();
		do {
			printer.setCurrent(total);
			if (total % 128 == 0) {
				printer.print(writer);
				writer.println();
			}
			total += 8;
		} while (System.currentTimeMillis() - start < 10000);
		
		printer.done();
		
		printer.print(writer);
		writer.println();
	}
	
	@Test
	public void testProgressBar() {
		long size = 1024 * 1024 * 16;
		ProgressPrinter printer = new ProgressPrinter(size);
		
		PrintWriter writer = new PrintWriter(System.out, true);
		for (long total = 0; total < size; total++) {
			printer.setCurrent(total);
			if (total % 128 == 0) {
				printer.print(writer);
				writer.println();
			}
		}

		printer.done();
		
		printer.print(writer);
		writer.println();
	}
}
