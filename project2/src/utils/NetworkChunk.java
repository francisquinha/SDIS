package utils;

import java.io.Serializable;

public class NetworkChunk extends Chunk implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -4390890677046075634L;
	private byte[] body;
	
	public NetworkChunk(int chunk_no, int file_id, int min_rep_degree, int desired_d, int key) {
		super(chunk_no, file_id, min_rep_degree, desired_d, key);		
	}
	
	public NetworkChunk(int chunk_no,int file_id, int min_rep_degree, int desired_d, int key, byte[] body){
		super(chunk_no, file_id, min_rep_degree, desired_d, key);
		this.setBody(body);
	}
	
	public NetworkChunk(FileSystemChunk c, byte[] body){
		super(c.getChunk_nr(),c.getFile_id(), c.getRepDegree(), c.getDesiredDegree(), c.getKey());
		this.body=body;
	}
	
	public NetworkChunk(byte[] body) {
		super();
		this.setBody(body);
	}

	public byte[] getBody() {
		return body;
	}

	public void setBody(byte[] body) {
		this.body = body;
	}



}
