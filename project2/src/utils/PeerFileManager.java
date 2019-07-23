package utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Manages the directory created to store all the backup information for a peer
 * The directory structure is:
 *  ./server_id/file_id/chunk_no.info -> has all the info about the node
 *  ./server_id/file_id/chunk_no.body -> has the body of the node
 * The info is loaded from the disk to be used by the peer, the bodys are only referenced if necessary.
 *
 */
public class PeerFileManager {

	private static File peer_directory;
	private static File BASE_DIRECTORY;

	public static boolean openOrCreate(String server_id){
		String BASE_DIR = System.getProperty("user.home")+"/STORAGE_sdis1516-t6g02/"; 
		BASE_DIRECTORY = new File(BASE_DIR);
		if(!BASE_DIRECTORY.exists()){
			BASE_DIRECTORY.mkdir();
		}
		else if(BASE_DIRECTORY.isFile()){
			System.out.println("Can not create storage directory, file exists with the same name");
		}
		peer_directory = new File(BASE_DIRECTORY,server_id);
		if(peer_directory.exists() && peer_directory.isDirectory()){
			return true;
		}
		else{
			if(peer_directory.mkdir()){
				return true;
			}
			else{
				System.out.println("Unable to create directory: "+server_id);
				return false;
			}
		}		
	}

	public static HashMap<Integer, HashMap<Integer, FileSystemChunk>> loadBackupInformation(String server_id) throws IOException, ClassNotFoundException{
		HashMap<Integer, HashMap<Integer, FileSystemChunk>> chunk_map = new HashMap<Integer, HashMap<Integer, FileSystemChunk>>();
		if(openOrCreate(server_id)){  
			DirectoryStream<Path> stream = Files.newDirectoryStream(peer_directory.toPath());
			for (Path file_dir: stream) {
				if(file_dir.toFile().isDirectory()){
					HashMap<Integer, FileSystemChunk> file_chunks = new HashMap<Integer, FileSystemChunk>();
					DirectoryStream<Path> chunk_stream = Files.newDirectoryStream(file_dir,"*.info");
					int file_id = 0;
					boolean once = false;
					for(Path chunk_file: chunk_stream){

						FileInputStream fis = new FileInputStream(chunk_file.toFile());
						ObjectInputStream ois = new ObjectInputStream(fis);
						FileSystemChunk c = (FileSystemChunk) ois.readObject();
						file_chunks.put(c.getChunk_nr(),c);
						if(!once){
							file_id=c.getFile_id();
							once=true;							
						}
						ois.close();
						fis.close();

					}
					chunk_map.put(file_id, file_chunks);
				}	               
			}
		}
		return chunk_map;
	}

	public static byte[] getChunkBody(FileSystemChunk chunk){
		if(!verifyPaths(chunk)){
			System.out.println("Local directories compromised.");
		}
		Path chunk_body_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".body");
		if(!chunk_body_path.toFile().exists()){
			return null;
		}
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(chunk_body_path.toFile());
		} catch (FileNotFoundException e) {
			System.out.println("Error opening chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
		byte[] chunk_body = new byte[(int) chunk_body_path.toFile().length()];
		int length = 0;
		try {
			length = fis.read(chunk_body);
			fis.close();
		} catch (IOException e) {
			System.out.println("Error reading chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
		if(length <= 0){
			return new byte[0];
		}
		return Arrays.copyOf(chunk_body, length);

	}

	public static void setChunkBody(FileSystemChunk chunk,byte[] body){
		if(!verifyPaths(chunk)){
			System.out.println("Local directories compromised.");
		}
		Path chunk_body_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".body");
		//remove current body
		chunk_body_path.toFile().delete();
		//create new one
		try {
			if(!chunk_body_path.toFile().createNewFile()){
				System.out.println("Error creating chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			}
		} catch (IOException e) {
			System.out.println("Error creating chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
		//write new body to file
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(chunk_body_path.toFile());
		} catch (FileNotFoundException e) {
			System.out.println("Error opening chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
		try {
			fos.write(body);
			fos.close();
		} catch (IOException e) {
			System.out.println("Error writing chunk body file in file_id: "+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
	}

	public static void deleteFileFolder(String file_id){
		File folder = new File(peer_directory.getPath() + "/" + file_id);
		folder.delete();
	}

	public static void deleteChunk(FileSystemChunk chunk){
		if(!verifyPaths(chunk)){
			System.out.println("Local directories compromised.");
		}

		Path chunk_body_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".body");
		if(chunk_body_path.toFile().exists()){		
			chunk_body_path.toFile().delete();
		}		
		Path chunk_info_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".info");
		if(chunk_info_path.toFile().exists()){
			chunk_info_path.toFile().delete();			
		}
	}

	public static void deleteChunkBody(FileSystemChunk chunk){
		if(!verifyPaths(chunk)){
			System.out.println("Local directories compromised.");
		}
		Path chunk_body_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".body");
		//remove current body
		chunk_body_path.toFile().delete();
	}

	public static void setChunkInfo(FileSystemChunk chunk){
		if(!verifyPaths(chunk)){
			System.out.println("Local directories compromised.");
		}
		Path chunk_info_path = Paths.get(peer_directory.getPath(),String.valueOf(chunk.getFile_id()),chunk.getChunk_nr()+".info");
		chunk_info_path.toFile().delete();
		try {
			if(!chunk_info_path.toFile().createNewFile()){
				System.out.println("Error creating chunk info file in file_id:"+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			}
		} catch (IOException e) {
			System.out.println("Error creating chunk info file in file_id:"+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
		FileOutputStream fos = null;

		try {
			fos = new FileOutputStream(chunk_info_path.toFile());
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(chunk);
			oos.close();
			fos.close();
		} catch (IOException e) {
			System.out.println("Error opening chunk info file in file_id:"+chunk.getFile_id()+" chunk_no: "+chunk.getChunk_nr());
			e.printStackTrace();
		}
	}

	private static boolean verifyPaths(FileSystemChunk chunk){
		if(!peer_directory.exists()){
			return false;
		}
		File chunk_file_dir = new File(peer_directory,String.valueOf(chunk.getFile_id()));
		if(!chunk_file_dir.exists()){
			chunk_file_dir.mkdir();
		}
		return true;

	}

}
