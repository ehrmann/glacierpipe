package glacierpipe.io;

import java.io.InputStream;
import java.io.OutputStream;

public interface IOBuffer {

	public long getCapacity();
	public long getLength();
	public long getRemaining();
	
	public OutputStream getOutputStream();
	public InputStream getInputStream(); 
	
}
