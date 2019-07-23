package test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Enumeration;

import client.ClientMetaData;
import client.TestAppInterface;
import utils.Constants;
import utils.NetworkChunk;
import utils.PortForwarding;
import utils.Utils;

public class Clienttest {
	public static void main(String args[]) throws InterruptedException{
		try {
	    Registry registry = LocateRegistry.getRegistry();
	    TestAppInterface stub = (TestAppInterface) registry.lookup(Constants.REQUEST_HANDLER_REMOTE_NAME);	    
	    ClientMetaData response = stub.register("ivoadf","123456",InetAddress.getByName("95.136.97.145"),0);
	    System.out.println("REGISTERED");
	    int rep = stub.backup("/home/ivoadf/Uni/SDIS/SVN_P2/sdis1516-t6g02-p2/project2/src/classes/example.txt",1);	    
	    System.out.println("BACKED UP WITH REP DEGREE: "+rep);
	    stub.restore("example.txt");
	    System.out.println("RESTORED");
	    System.out.println("response: " + response);
	} catch (Exception e) {
	    System.err.println("Client exception: " + e.toString());
	    e.printStackTrace();
	}		
	}

}
