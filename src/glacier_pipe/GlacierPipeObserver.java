package glacier_pipe;

public interface GlacierPipeObserver {

	public void gotUploadId(String uploadId);
	
	public void startBuffering(int partId);
	public void buffering(int partId, long dataRead);
	public void endBuffering(int partId);
	
	public void computedTreeHash(int partId, byte[] treeHash);
	
	public void startPartUpload(int partId);
	public void partUploading(int partId, long dataUploaded);
	public void endPartUpload(int partId);
	
	public void exceptionUploadingPart(int partId, Exception e, int attempt, boolean retrying);
	public void sleepingBeforeRetry(long sleepingFor);
	
	public void done(byte[] finalTreeHash, String location);
	public void fatalException(Exception e);
}
