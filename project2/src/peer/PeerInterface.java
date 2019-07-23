package peer;

import utils.FileSystemChunk;
import utils.NetworkChunk;
import utils.Pair;

import javax.crypto.SealedObject;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;

public interface PeerInterface extends Remote{

	public Pair<Integer, PeerInterface> getSuccessor() throws RemoteException;
	
	public Pair<Integer, PeerInterface> getPredecessor() throws RemoteException;
	
	public Pair<Integer, PeerInterface> lookup(int k) throws RemoteException;
	
	public int getMaxPeers() throws RemoteException;
	
	public LinkedList<Pair<Integer, PeerInterface>> chordNotify(Pair<Integer, PeerInterface> p) throws RemoteException;
	
	public void check() throws RemoteException;
	
	public int storeChunk(NetworkChunk chunk, ArrayList<Integer> l) throws RemoteException;
	
	public Pair<Integer, PeerInterface> getChunkHolder(int file_id,int chunk_nr) throws RemoteException;
	
	public NetworkChunk getChunk(int file_id, int chunk_nr) throws RemoteException;
	
	public void deleteChunk(int file_id, int chunk_nr) throws RemoteException;
	
	public int putChunk(NetworkChunk c) throws RemoteException;
	
	public int releaseChunk(FileSystemChunk c, ArrayList<Integer> l) throws RemoteException;
	
	public int checkChunk(FileSystemChunk c, ArrayList<Integer> l) throws RemoteException;
}

