package utils;

import java.io.Serializable;

public class FileSystemChunk extends Chunk implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -638818945690355073L;

    /**
     * chunk store status
     * 1 - chunk body stored in hard drive;
     * 0 - chunk not stored due to lack of space;
     * -1 - chunk not stored anywhere on the network
     */
    private int status;
    private boolean delete = false;

	public FileSystemChunk(int chunk_no, int file_id, int min_rep_degree, int desired_d, int key) {
		super(chunk_no, file_id, min_rep_degree, desired_d, key);
		PeerFileManager.setChunkInfo(this);		
	}

	public FileSystemChunk(NetworkChunk c){
		super(c.getChunk_nr(),c.getFile_id(),c.getRepDegree(), c.getDesiredDegree(), c.getKey());
	}

    public FileSystemChunk(NetworkChunk c, int status){
        super(c.getChunk_nr(),c.getFile_id(),c.getRepDegree(), c.getDesiredDegree(), c.getKey());
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
    
    public void setDelete(boolean s){
    	delete = s;
    }
    
    public boolean getDelete(){
    	return delete;
    }
}
