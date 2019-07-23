package utils;

import java.io.Serializable;

public class FileMetaData implements Serializable{	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4095180810313856934L;
	int file_id;
	int number_chunks;
	
	public FileMetaData(int file_id, int number_chunks) {		
		this.file_id = file_id;
		this.number_chunks = number_chunks;
	}
	
	public int getFile_id() {
		return file_id;
	}
	
	public void setFile_id(int file_id) {
		this.file_id = file_id;
	}
	
	public int getNumber_chunks() {
		return number_chunks;
	}
	
	public void setNumber_chunks(int number_chunks) {
		this.number_chunks = number_chunks;
	}
	
	
	

}
