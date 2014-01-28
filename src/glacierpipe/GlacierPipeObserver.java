/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package glacierpipe;

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
