package glacier_pipe.io;

public interface StreamObserver {

	public void streamOpened();
	public void streamClosed(boolean hadException);
	
}
