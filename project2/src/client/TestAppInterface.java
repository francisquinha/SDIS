package client;

import java.net.InetAddress;
import java.rmi.Remote;

public interface TestAppInterface extends Remote{
	/** USER IDENTIFICATION **/
	ClientMetaData register(String username,String password, InetAddress access_point, int access_point_key) throws Exception;
	ClientMetaData login(String username,String password, InetAddress access_point, int access_point_key) throws Exception;
	
	/** USER OPERATIONS **/
	int backup(String file_path,int replication_degree) throws Exception;
	void delete(String file_name) throws Exception;
	boolean restore(String file_name) throws Exception;
	
}
