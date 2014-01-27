package glacier_pipe.io;

public interface InputStreamObserver extends StreamObserver {

	public void bytesRead(long bytes);
	public void bytesSkipped(long skipped);
	
}
