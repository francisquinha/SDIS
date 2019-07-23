package client;

import java.io.Serializable;
import java.security.MessageDigest;
import java.util.HashMap;

import utils.FileMetaData;

public class ClientMetaData implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 735186986797663369L;
	private HashMap<String,FileMetaData> filename_map = new HashMap<String,FileMetaData>();	
	
	public ClientMetaData() {
		super();			
	}	
	
	public void addFile(String file_name, int file_id,int nr_chunks) {			
		filename_map.put(file_name, new FileMetaData(file_id,nr_chunks));
	}
	
	public void removeFile(String file_name){
		filename_map.remove(file_name);
	}
	
	public FileMetaData getFileMetadata(String file_name){
		return filename_map.get(file_name);		
	}
	
	
}
