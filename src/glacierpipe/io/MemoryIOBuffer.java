package glacierpipe.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MemoryIOBuffer implements IOBuffer {

	protected static final int DEFAULT_ARRAY_SIZE = 1024 * 1024;
	
	protected final byte[][] buffer;
	protected final long capacity;
	
	protected int streamCount = 0;
	protected long length = 0;

	public MemoryIOBuffer(long capacity, int arraySize) {
		if (capacity / arraySize > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("size too big for given arraySize");
		}
		
		buffer = new byte[(int)(1 + ((capacity - 1) / arraySize))][arraySize];
		this.capacity = capacity;
	}
	
	public MemoryIOBuffer(long size) {
		this(size, DEFAULT_ARRAY_SIZE);
	}
	
	@Override
	public long getCapacity() {
		return this.capacity;
	}
	
	@Override
	public long getLength() {
		return this.length;
	}
	
	@Override
	public long getRemaining() {
		return this.capacity - this.length;
	}
	
	@Override
	public OutputStream getOutputStream() {
		if (this.streamCount == 0) {
			this.streamCount = -1;
			return new MemoryOutputStream();
		} else if (this.streamCount < 0) {
			throw new IllegalStateException("An OutputStream is already open");
		} else {
			throw new IllegalStateException(this.streamCount + " InputStream(s) is/are already open");
		}
	}

	@Override
	public InputStream getInputStream() {
		if (this.streamCount >= 0) {
			this.streamCount++;
			return new MemoryInputStream();
		} else {
			throw new IllegalStateException("An OutputStream is already open");
		}
	}

	protected class MemoryOutputStream extends OutputStream {
		
		long position = 0;
		int currentIndex = 0;
		int currentBuffer = 0;
		boolean closed = false;
		
		public MemoryOutputStream() {
			MemoryIOBuffer.this.length = 0;
		}
		
		@Override
		protected void finalize() throws Throwable {
			try {
				this.close();
			} finally {
				super.finalize();
			}
		}

		@Override
		public void write(int b) throws IOException {
			if (currentBuffer >= MemoryIOBuffer.this.buffer.length) {
				throw new IOException("Buffer full; " + this.position + " bytes written");
			} else if (this.closed) {
				throw new IOException("buffer closed");
			}
			
			MemoryIOBuffer.this.length++;
			
			MemoryIOBuffer.this.buffer[currentBuffer][currentIndex] = (byte)b;
			this.position++;
			if (++currentIndex >= MemoryIOBuffer.this.buffer[currentBuffer].length) {
				currentIndex = 0;
				currentBuffer++;
			}
		}

		@Override
		public void write(byte[] buf, int off, int len) throws IOException {
			if (len < 0) {
				throw new IllegalArgumentException("len was negative");
			} else if (off < 0) {
				throw new IllegalArgumentException("off was negative");
			} else if (off > buf.length - len) {
				throw new IllegalArgumentException("off + len > buf.length");
			} else if (len > 0 && currentBuffer >= MemoryIOBuffer.this.buffer.length) {
				throw new IOException("Buffer full; " + this.position + " bytes written");
			} else if (this.closed) {
				throw new IOException("buffer closed");
			} else if (len > 0 && this.position + len > MemoryIOBuffer.this.capacity) { 
				throw new IOException("write() of length " + len + " will overflow internal buffer");
			} else if (this.closed) {
				throw new IOException(this.getClass().getSimpleName() + " already closed");
			}
			
			this.position += len;
			MemoryIOBuffer.this.length += len;
			
			while (len > 0) {
				MemoryIOBuffer.this.buffer[currentBuffer][currentIndex] = buf[off];
				
				if (++currentIndex >= MemoryIOBuffer.this.buffer[currentBuffer].length) {
					currentIndex = 0;
					currentBuffer++;
				}
				
				off++;
				len --;
			}
		}

		@Override
		public void close() throws IOException {
			if (!this.closed) {
				MemoryIOBuffer.this.streamCount++;
				this.closed = true;
			}
		}
	}
	
	protected class MemoryInputStream extends InputStream {
		
		long position = 0;
		long markPosition = 0;
		int currentIndex = 0;
		int markIndex = 0;
		int currentBuffer = 0;
		int markBuffer = 0;
		
		boolean closed = false;
		
		@Override
		protected void finalize() throws Throwable {
			try {
				this.close();
			} finally {
				super.finalize();
			}
		}
		
		@Override
		public void close() throws IOException {
			if (!this.closed) {
				MemoryIOBuffer.this.streamCount--;
				this.closed = true;
			}
		}

		@Override
		public int read() throws IOException {
			if (this.position >= MemoryIOBuffer.this.length) {
				return -1;
			} else if (this.closed) {
				throw new IOException("read() called on a closed stream");
			}
			
			int r = MemoryIOBuffer.this.buffer[currentBuffer][currentIndex] & 0xff;
			this.position++;
			
			if (++currentIndex >= MemoryIOBuffer.this.buffer[currentBuffer].length) {
				currentIndex = 0;
				currentBuffer++;
			}
			
			return r;
		}

		@Override
		public int read(byte[] buf, int off, int len) throws IOException {
			if (off < 0) {
				throw new IllegalArgumentException("off is negative");
			} else if (len < 0) {
				throw new IllegalArgumentException("len is negative");
			} else if (len > buf.length - off) {
				throw new IllegalArgumentException("off + len > buf.length");
			} else if (this.position >= MemoryIOBuffer.this.length) {
				return -1;
			} else if (this.closed) {
				throw new IOException("read() called on a closed stream");
			}
			
			final int read = (int)Math.min(MemoryIOBuffer.this.length - this.position, len);
			this.position += read;
			len = read;
			
			while (len > 0) {
				buf[off] = MemoryIOBuffer.this.buffer[currentBuffer][currentIndex];

				off++;
				len--;
				
				if (++currentIndex >= MemoryIOBuffer.this.buffer[currentBuffer].length) {
					currentIndex = 0;
					currentBuffer++;
				}
			}
			
			return read;
		}

		@Override
		public int available() throws IOException {
			if (this.closed) {
				throw new IOException(this.getClass().getSimpleName() + " already closed");
			}
			
			return (int)Math.min(MemoryIOBuffer.this.length - this.position, Integer.MAX_VALUE);
		}

		@Override
		public long skip(long n) throws IOException {
			if (this.closed) {
				throw new IOException(this.getClass().getSimpleName() + " already closed");
			}
			
			long toSkip = Math.min(n, MemoryIOBuffer.this.length - this.position);
			this.position += toSkip;
			return toSkip;
		}

		@Override
		public boolean markSupported() {
			return true;
		}

		@Override
		public synchronized void mark(int readlimit) {
			this.markPosition = this.position;
			this.markBuffer = this.currentBuffer;
			this.markIndex = this.currentIndex;
		}

		@Override
		public synchronized void reset() throws IOException {
			if (this.closed) {
				throw new IOException(this.getClass().getSimpleName() + " already closed");
			}

			this.position = markPosition;
			this.currentIndex = this.markIndex;
			this.currentBuffer = this.markBuffer;
		}
	}
	
}
