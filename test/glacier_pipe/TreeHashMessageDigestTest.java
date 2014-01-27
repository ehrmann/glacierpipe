package glacier_pipe;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;

import com.amazonaws.services.glacier.TreeHashGenerator;
import com.amazonaws.util.BinaryUtils;

public class TreeHashMessageDigestTest {

	@Ignore
	@Test
	public void testTreeHashMessageDigest() throws NoSuchAlgorithmException {
		
		Random r = new Random(0x42);
		TreeHashMessageDigest digest = new TreeHashMessageDigest(MessageDigest.getInstance("SHA-256"));
		
		// Since Tree Hash chunk sizes are 1 MB, cycle through lots of sizes both on and off the 1 MB boundary
		for (int size = 0; size <= 1024 * 1024 * 64; size += 1024 * 512) {
			
			// Apparently the AWS SDK can't do this.
			if (size == 0) {
				continue;
			}
			
			byte[] toHash = new byte[size];
			r.nextBytes(toHash);
			
			byte[] actual = digest.digest(toHash);
			byte[] expected = BinaryUtils.fromHex(TreeHashGenerator.calculateTreeHash(new ByteArrayInputStream(toHash)));
			
			assertArrayEquals("Failed for size " + (size / 1024) + " K", actual, expected);
		}
		
	}
	
	@Test
	public void testRandomByteArrays() throws NoSuchAlgorithmException {
		
		final int trials = 64;
		Random r = new Random(0x42);
		TreeHashMessageDigest digest = new TreeHashMessageDigest(MessageDigest.getInstance("SHA-256"));
		
		for (int i = 0; i < trials; i++) {
			int size = r.nextInt(1024 * 1024 * 24) + 1;
			byte[] toHash = new byte[size];
			r.nextBytes(toHash);
			
			int offset = 0;
			for (int j = 0; j < 10 && offset < toHash.length; j++) {
				int op = r.nextInt(2);
				if (op == 0) {
					digest.update(toHash[offset++]);
				} else if (op == 1) {
					int len = r.nextInt(toHash.length - offset);
					digest.update(toHash, offset, len);
					offset += len;
				}
			}
			
			digest.update(toHash, offset, toHash.length - offset);
		
			byte[] actual = digest.digest();
			byte[] expected = BinaryUtils.fromHex(TreeHashGenerator.calculateTreeHash(new ByteArrayInputStream(toHash)));
			
			assertArrayEquals("Failed for i = " + i, actual, expected);
		}
		
	}
}
