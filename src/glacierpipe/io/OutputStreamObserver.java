package glacierpipe.io;

public interface OutputStreamObserver extends StreamObserver {

	public void bytesWritten(int bytes);
	
}
