package glacierpipe;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class TreeHashMessageDigest extends MessageDigest {

	protected final int CHUNK_SIZE = 1024 * 1024;
	
	protected final MessageDigest digest;
	protected final ArrayList<byte[]> hashChunks = new ArrayList<byte[]>(16);
	protected int bytesInDigest = 0;
	
	public TreeHashMessageDigest(MessageDigest digest) {
		super("SHA256TreeHash");
		this.digest = Objects.requireNonNull(digest, "digest was null");
	}

	@Override
	protected byte[] engineDigest() {
		if (this.bytesInDigest > 0 || this.hashChunks.isEmpty()) {
			this.bytesInDigest = 0;
			this.hashChunks.add(this.digest.digest());
		}
		
		List<byte[]> active = this.hashChunks;
		List<byte[]> replacement = new ArrayList<byte[]>(hashChunks.size() / 2 + 1);
		
		while (active.size() > 1) {
			replacement.clear();
			
			Iterator<byte[]> iterator = active.iterator();
			while (iterator.hasNext()) {
				byte[] left = iterator.next();
				
				if (iterator.hasNext()) {
					byte[] right = iterator.next();
					
					this.digest.update(left);
					this.digest.update(right);
					
					replacement.add(this.digest.digest());
				} else {
					replacement.add(left);
				}
			}
			
			List<byte[]> temp = active;
			active = replacement;
			replacement = temp;
		}
		
		byte[] result = active.get(0);
		this.engineReset();
		return result;
	}

	@Override
	protected void engineReset() {
		this.hashChunks.clear();
		this.hashChunks.trimToSize();
		this.digest.reset();
		this.bytesInDigest = 0;
	}

	@Override
	protected void engineUpdate(byte input) {
		if (this.bytesInDigest == CHUNK_SIZE) {
			hashChunks.add(this.digest.digest());
			this.bytesInDigest = 0;
		}
		
		this.digest.update(input);
		this.bytesInDigest++;
	}

	@Override
	protected void engineUpdate(byte[] buf, int off, int len) {
		while (len > 0) {
			if (this.bytesInDigest == CHUNK_SIZE) {
				hashChunks.add(this.digest.digest());
				this.bytesInDigest = 0;
			}
			
			int toDigest = Math.min(CHUNK_SIZE - this.bytesInDigest, len);
			this.digest.update(buf, off, toDigest);

			off += toDigest;
			len -= toDigest;
			this.bytesInDigest += toDigest;
		}
	}
}
