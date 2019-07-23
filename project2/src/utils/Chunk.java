package utils;

import java.io.Serializable;

public abstract class Chunk implements Serializable {
    /**
     *
     */
    private static final long serialVersionUID = 8310356029796453023L;
    private int chunk_nr;
    private int file_id;
    private int rep_degree = 0;
    private int desired_rep_degree;
    private long last_check = 0;
    //key this chunk was mapped to
    private int key;

    public Chunk(int chunk_no, int file_id, int min_rep_degree, int desired_d, int key) {
        this.setChunk_nr(chunk_no);
        this.setFile_id(file_id);
        this.setRepDegree(min_rep_degree);
        this.setDesiredDegree(desired_d);
        this.setKey(key);
    }

    public Chunk() {
    }

    public Chunk(int file_id, int chunk_nr, int key) {
        this.setFile_id(file_id);
        this.setChunk_nr(chunk_nr);
        this.setKey(key);
    }

    public int getChunk_nr() {
        return chunk_nr;
    }

    public void setChunk_nr(int chunk_nr) {
        this.chunk_nr = chunk_nr;
    }

    public int getFile_id() {
        return file_id;
    }

    public void setFile_id(int file_id) {
        this.file_id = file_id;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int k) {
        key = k;
    }

    public int getRepDegree() {
        return rep_degree;
    }

    public void setRepDegree(int rep_degree) {
        this.rep_degree = rep_degree;
    }
    
    public int getDesiredDegree(){
    	return desired_rep_degree;
    }
    
    public void setDesiredDegree(int d){
    	desired_rep_degree = d;
    }
    
    public void setLastCheck(long c){
    	last_check = c;
    }
    
    public long getLastCheck(){
    	return last_check;
    }


}
