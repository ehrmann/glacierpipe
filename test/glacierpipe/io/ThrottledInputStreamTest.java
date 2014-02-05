package glacierpipe.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class ThrottledInputStreamTest {

	protected final Random random = new Random(42);
	
	@Test
	public void testThrottledInputStreamByteRead() throws IOException {
		byte[] buffer = new byte[1024 * 512];
		random.nextBytes(buffer);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream(buffer.length);
		
		long time = System.currentTimeMillis();
		
		try (InputStream in = new ThrottledInputStream(new ByteArrayInputStream(buffer), 1024 * 64)) {
			int read;
			while ((read = in.read()) >= 0) {
				out.write(read);
			}
		}
		
		time = System.currentTimeMillis() - time;
		
		Assert.assertEquals(8.0, time / 1000.0, 2.0);
		Assert.assertArrayEquals(buffer, out.toByteArray());
	}
	
	
	@Test
	public void testThrottledInputStreamBlockRead() throws IOException {
		byte[] readBuffer = new byte[8192];
		
		byte[] buffer = new byte[1024 * 512];
		random.nextBytes(buffer);
		
		ByteArrayOutputStream out = new ByteArrayOutputStream(buffer.length);
		int chunks = 0;
		
		long time = System.currentTimeMillis();
		
		try (InputStream in = new ThrottledInputStream(new ByteArrayInputStream(buffer), 1024 * 64)) {
			int read;
			while ((read = in.read(readBuffer)) >= 0) {
				out.write(readBuffer, 0, read);
				chunks++;
			}
		}
		
		time = System.currentTimeMillis() - time;
		
		Assert.assertEquals(8.0, time / 1000.0, 2.0);
		Assert.assertArrayEquals(buffer, out.toByteArray());
		System.err.printf("Average chunk size: %.2f\n", buffer.length / (double)chunks);
	}
}
