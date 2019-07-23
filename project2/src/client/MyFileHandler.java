package client;

import utils.NetworkChunk;
import utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * File Handler class, used to load chunks from a file, generate fileId, get file name and extension
 */
public class MyFileHandler extends File {

    /**
     *
     */
    private static final long serialVersionUID = 618999960343249779L;

    private final static int MAX_CHUNK_SIZE = 64000;
    FileInputStream file_input_stream;
    private int number_chunks_read = 0;
    private int file_id;

    public MyFileHandler(String pathname) throws Exception {
        super(pathname);
        this.file_id = this.getFileId();
        try {
            file_input_stream = new FileInputStream(this);
        } catch (FileNotFoundException e) {
            System.out.println("File " + pathname + " does not exist.");
            throw new Exception("File " + pathname + " does not exist.");
        }
    }

    public static String getNameNoExtension(String name) {
        String ret = "";
        String[] file_split = name.split("\\.");
        for (int i = 0; i < file_split.length - 1; i++) {
            ret += file_split[i];
        }
        return ret;
    }

    public static String getExtension(String name) {
        String[] file_split = name.split("\\.");
        if (file_split.length < 1)
            return "";
        return file_split[file_split.length - 1];
    }

    public NetworkChunk nextChunk(int rep_degree, int key, SecretKey enc_key) {
        try {
            byte[] body = this.getNextBody(number_chunks_read);
            if (body.length == 0)
                return null;
            byte[] enc_body = Utils.encrypt(body,enc_key);
            NetworkChunk c = new NetworkChunk(number_chunks_read,file_id, rep_degree, rep_degree, key, enc_body);
            number_chunks_read++;
            return c;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public byte[] getNextBody(int num_read) throws Exception {
        byte[] file_bytes = new byte[MAX_CHUNK_SIZE];
        int read_length;
        read_length = file_input_stream.read(file_bytes);
        if (read_length <= 0) {
            return new byte[0];
        }
        return Arrays.copyOf(file_bytes, read_length);
    }

    public int getFileId() {
    	System.out.println("Generating file id with "+this.getName() + this.lastModified() + this.length());
        String hash_input = this.getName() + this.lastModified() + this.length();    
        
        return Math.abs(hash_input.hashCode());
    }
}
