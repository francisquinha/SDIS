package peer;

import utils.*;

import javax.crypto.SealedObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Peer implements PeerInterface {

	//Using Pair<Integer, PeerInterface> to keep the guid of each node without needing to ask the node for its guid
	private LinkedList<Pair<Integer, PeerInterface>> finger_table = new LinkedList<Pair<Integer, PeerInterface>>();
	private LinkedList<Pair<Integer, PeerInterface>> successor_list = new LinkedList<Pair<Integer, PeerInterface>>();
	private Pair<Integer, PeerInterface> predecessor;
	private Pair<Integer, PeerInterface> pair;
	private PeerInterface peer_interface = null;
	private int n;
	private int m;
	private int guid = 0;
	private int next = 0;
	private String remote_object_name;
	private HashMap<Integer, HashMap<Integer, FileSystemChunk>> chunk_map = new HashMap<Integer, HashMap<Integer, FileSystemChunk>>();
	private long available_space_bytes;
	private String peer_name;
	private boolean revived = false;

	private void addShutdownhook(){
		Thread shutdown_thread = new Thread() {
			public void run() {
				System.out.println("\nStopping execution in response to shutdown hook.");
				PortForwarding.shutdown();
				System.out.println("Shutdown hook complete");
			}
		};
		Runtime.getRuntime().addShutdownHook(shutdown_thread);
	}

	/**
	* Join known chord and receive random key
	*
	* @param a chord access point
	* @param g chord access point guid, which is the remote object's name
	*/
	public Peer(String name,InetAddress a, int g, int space) throws Exception {
		Properties props = System.getProperties();
		props.setProperty("java.rmi.server.hostname", a.getHostName());
		if(!Constants.LAN_MODE){
			addShutdownhook();

			try{
				PortForwarding.doPortForwarding();
			}
			catch(Exception e){
				System.err.println("Port forwarding error");
			}
		}
		peer_name = name;
		available_space_bytes = space;
		PeerInterface p = null;

		try {
			p = (PeerInterface) Naming.lookup("//" + a.getHostAddress() + ":1099/" + Integer.toString(g));
		} catch (Exception e) {
			throw new Exception("Unable to connect to peer " + a.getHostAddress() + " with GUID:" + g);
		}
		n = p.getMaxPeers();

		//generating and trying keys
		List<Integer> keys = new ArrayList<Integer>();
		int i = 0;
		for (; i < n; i++) {
			keys.add(i);
		}
		Collections.shuffle(keys);
		i = 0;
		int k = 1;
		boolean joined = false;
		do {
			try {
				k = keys.get(i);
				chunk_map = PeerFileManager.loadBackupInformation(peer_name);
				join(k, p);
				System.out.println("Joined network with " + n + " max peers with key: " + k);
				joined = true;
			} catch (RemoteException e) {
				//Unable to connect to peer or Unable to create RMI register, retrying keys will not work
				throw new Exception(e.getMessage());
			} catch (Exception e) {
				//Does nothing
			}
			i++;
		} while (!joined && i < n);
		if (!joined && i == n) {
			throw new Exception("Tried to join a chord that is already full.");
		}
		revived = false;
	}

	/**
	* Join known chord with given key
	*
	* @param a chord access point
	* @param g chord access point guid, which is the remote object's name
	* @param k guid to join with
	* @throws Exception
	*/

	public Peer(String name,InetAddress a, int g, int k, int space) throws Exception {
		Properties props = System.getProperties();
		props.setProperty("java.rmi.server.hostname", a.getHostName());
		if(!Constants.LAN_MODE){
			addShutdownhook();
			try{
				PortForwarding.doPortForwarding();
			}
			catch(Exception e){
				System.err.println("Port forwarding error");
			}
		}
		peer_name = name;
		available_space_bytes = space;
		PeerInterface p = null;
		chunk_map = PeerFileManager.loadBackupInformation(peer_name);
		try {
			p = (PeerInterface) Naming.lookup("//" + a.getHostAddress() + ":1099/" + Integer.toString(g));
		} catch (Exception e) {
			throw new Exception("Unable to connect to peer " + a.getHostAddress() + " with GUID:" + g);
		}
		try {
			join(k, p);
			System.out.println("Joined network with " + n + " max peers");
		} catch (Exception e) {
			throw new Exception("Join Exception: " + e.getMessage());
		}
		revived = false;
	}

	/**
	* Create a chord with n maximum peers
	*
	* @param n maximum peers
	*/
	public Peer(String name,int n, int space) throws Exception {
		if(!Constants.LAN_MODE){
			addShutdownhook();
			try{
				PortForwarding.doPortForwarding();
			}
			catch(Exception e){
				System.err.println("Port forwarding error");
			}
		}
		peer_name = name;
		available_space_bytes = space;

		this.n = n;
		chunk_map = PeerFileManager.loadBackupInformation(peer_name);
		try {
			createRMI();
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception("Unable to bind PeerInterface to RMI register.");
		}
		revived = false;
	}

	public void initFingerSucc() {
		m = (int) (Math.log(n) / Math.log(2));
		int i = 0;
		for (; i < m; i++) {
			finger_table.add(pair);
		}
		for (i = 0; i < m; i++) {
			successor_list.add(pair);
		}
	}

	/**
	* Function called to create RMI
	* guid must be defined at the time of call
	* https://alephnull.uk/content/setup-java-rmi-over-internet
	* @throws Exception
	*/
	private void createRMI() throws Exception {
		if(!Constants.LAN_MODE){
			Properties props = System.getProperties();
			props.setProperty("java.rmi.server.hostname", Utils.getExternalIp());
			peer_interface = (PeerInterface) UnicastRemoteObject.exportObject(this, 1100); // to 1100
		}
		else{
			Properties props = System.getProperties();
			props.setProperty("java.rmi.server.hostname", Utils.getLocalIP());
			peer_interface = (PeerInterface) UnicastRemoteObject.exportObject(this, 0); // to open port
		}
		//remote object name is the guid
		remote_object_name = "//" + Utils.getLocalIP() + ":1099/" + Integer.toString(guid);
		Naming.rebind(remote_object_name, peer_interface);
		pair = new Pair<Integer, PeerInterface>(guid, peer_interface);
		initFingerSucc();
	}

	public Pair<Integer, PeerInterface> getSuccessor() {
		try{
			return successor_list.getFirst();
		}
		catch(NoSuchElementException e){
			return null;
		}
	}

	/**
	* Sets first successor on successor list
	*
	* Gets chunks where peer has rep_degree higher than 1, meaning that successor should have had the chunks, and sends them
	*
	* @param p
	*/
	public synchronized void setSuccessor(Pair<Integer, PeerInterface> p) {
		//not going to send itself the chunks
		if(p.first == guid){
			if(predecessor == null || predecessor.first == guid){
				return;
			}
			//sets predecessor as successor if new successor is itself and has a predecessor different than self
			p = predecessor;
		}
		successor_list.set(0, p);
		if(revived){
			Set<Entry<Integer, HashMap<Integer, FileSystemChunk>>> s0 = chunk_map.entrySet();
			Set<Entry<Integer, FileSystemChunk>> s1 = null;
			for (Entry<Integer, HashMap<Integer, FileSystemChunk>> e0 : s0) {
				s1 = e0.getValue().entrySet();
				for (Entry<Integer, FileSystemChunk> e1 : s1) {
					FileSystemChunk f = e1.getValue();
					if(f.getStatus() == -1 || f.getRepDegree() == 1){
						continue;
					}
					NetworkChunk c = new NetworkChunk(f, PeerFileManager.getChunkBody(f));
					if(f.getStatus() == 1){
						c.setRepDegree(c.getRepDegree() - 1);
					}
					try{
						p.second.storeChunk(c, new ArrayList<Integer>());
					}
					catch(RemoteException e){
						//failed
					}
				}
			}

		}
	}

	public Pair<Integer, PeerInterface> getPredecessor() {
		return predecessor;
	}

	public void setPredecessor(Pair<Integer, PeerInterface> p) {
		predecessor = p;
	}

	/**
	* Sets successors to first successor's successors
	*
	* @param l
	*/
	public void setSuccessors(LinkedList<Pair<Integer, PeerInterface>> l) {
		for (int i = 0; i < m - 1; i++) {
			successor_list.set((i + 1), l.get(i));
		}
	}

	/**
	* Removes successor because it either failed or left the network
	* Adds this peer as last on successor list to keep the list size
	*/
	public void removeSuccessor() {
		successor_list.remove(0);
		successor_list.add(pair);
		setSuccessor(getSuccessor());
	}

	public void setFinger(int i, Pair<Integer, PeerInterface> p) {
		if (i == 0) {
			setSuccessor(p);
		}
		finger_table.set(i, p);
	}

	public void setFingerTable() {
		int i = 0;
		Pair<Integer, PeerInterface> p = getSuccessor();
		for (; i < m; i++) {
			finger_table.set(i, p);
		}
	}

	public Pair<Integer, PeerInterface> lookup(int k) throws RemoteException {
		if (k >= n) {
			return new Pair<Integer, PeerInterface>(n, null);
		}
		if (k == guid) {
			return pair;
		}
		if (predecessor != null) {
			if (predecessor.first == guid) {
				return pair;
			}
			if (Utils.lookupUtil(predecessor.first, k, guid)) {
				return pair;
			}
		} else {
			return pair;
		}
		if (Utils.lookupUtil(guid, k, successor_list.getFirst().first)) {
			return successor_list.getFirst();
		}
		Pair<Integer, PeerInterface> p = closestPrecedingNode(k);
		if (p.first == guid) {
			return pair;
		}
		return p.second.lookup(k);
	}

	private Pair<Integer, PeerInterface> closestPrecedingNode(int k) {
		//from last to first means less iterations
		//there are more keys on the bottom ranges of the finger table, than on the top
		int i = m - 2;
		for (; i >= 0; i--) {
			Pair<Integer, PeerInterface> p1, p2;
			p1 = finger_table.get(i);
			p2 = finger_table.get(i + 1);
			if (Utils.lookupUtil(p1.first, k, p2.first)) {
				return p1;
			}
		}
		return finger_table.getLast();
	}

	private void stabilize() {
		Pair<Integer, PeerInterface> p = getSuccessor();
		Pair<Integer, PeerInterface> x = null;
		if(p!=null){
			try {
				x = p.second.getPredecessor();
			} catch (RemoteException e) {
				System.out.println("Successor failed");
				removeSuccessor();
				return;
			}
			if (p.first == guid) {
				if (x == null) {
					return;
				}
				setSuccessor(x);
			}
			if (x != null && Utils.lookupUtil(guid, x.first, p.first)) {
				System.out.println("Stabilized successor to " + x.first + " successor was " + p.first);
				setSuccessor(x);
			}
			try {
				LinkedList<Pair<Integer, PeerInterface>> l = getSuccessor().second.chordNotify(pair);
				setSuccessors(l);
			} catch (RemoteException e) {
				//means that successor failed
				removeSuccessor();
			}
		}
	}

	/**
	* Can only be notified by one peer at a time
	* @param p
	* @return
	* @throws RemoteException
	*/
	public synchronized LinkedList<Pair<Integer, PeerInterface>> chordNotify(Pair<Integer, PeerInterface> p) throws RemoteException {
		if (predecessor == null || Utils.lookupUtil(predecessor.first, p.first, guid)) {
			//new predecessor, need to send chunks belonging to predecessor
			ArrayList<FileSystemChunk> l = new ArrayList<FileSystemChunk>();
			predecessor = p;
			l = getChunksNewPred(predecessor.first);
			int s = l.size();
			/*
			* predecessor joined, giving the chunks belonging to it
			*/
			if(revived){
				System.out.println("New pred is " + p.first + ", sending " + s + " chunks");
				for (int i = 0; i < s; i++) {
					FileSystemChunk f = l.get(i);
					NetworkChunk c = new NetworkChunk(f, PeerFileManager.getChunkBody(f));
					if(f.getStatus() == -1){
						continue;
					}
					try {
						if (p.second.putChunk(c) == 1) {
							System.out.println("CHUNK STORED by pred");
							//chunk stored
							int d = f.getRepDegree();
							if (d == 1 && f.getStatus() == 1) {
								deleteChunk(f);
							} else {
								pair.second.releaseChunk(f, new ArrayList<Integer>());
							}
						}
					} catch (RemoteException e) {
						//Failed to release chunk
					}
				}
			}
		}
		return successor_list;
	}

	private void fixFingers() {
		next++;
		if (next > m) {
			next = 1;
		}
		try {
			int k = (int) (guid + Math.pow(2, next - 1));
			if (k >= n) {
				k -= n;
			}
			Pair<Integer, PeerInterface> p = lookup(k);
			finger_table.set((next - 1), p);
		} catch (RemoteException e) {

		}
	}

	public void check() throws RemoteException {

	}

	private void checkPredecessor() {
		try {
			if (predecessor != null) {
				predecessor.second.check();
			}
		} catch (RemoteException e) {
			System.out.println("predecessor failed");
			predecessor = null;
		}
	}

	private void checkChunkReplication(){
		Set<Entry<Integer, HashMap<Integer, FileSystemChunk>>> s0 = chunk_map.entrySet();
		Set<Entry<Integer, FileSystemChunk>> s1 = null;
		for (Entry<Integer, HashMap<Integer, FileSystemChunk>> e0 : s0) {
			s1 = e0.getValue().entrySet();
			for (Entry<Integer, FileSystemChunk> e1 : s1) {
				try {
					FileSystemChunk c = e1.getValue();
					long t = new Date().getTime();
					if(t - c.getLastCheck() > Constants.CHECK_REP_DELAY){
						c.setLastCheck(t);
						int degree = 0;
						int desired = c.getDesiredDegree();
						//using FileSystemChunk because it's just checking chunks, not sending them
						System.out.println("CHECKING " + c.getFile_id());
						Pair<Integer, PeerInterface> p = null;
						p = lookup(c.getKey());
						p = p.second.getChunkHolder(c.getFile_id(), c.getChunk_nr());
						if(p == null){
							continue;
						}
						if(p.first != guid){
							//not the chunk holder, let chunk holder check chunk
							continue;
						}
						//sets the rep degree to desired, since this is the chunk holder, this value is used in other functions to check if peer is chunk holder
						c.setRepDegree(desired);
						degree = checkChunk(c, new ArrayList<Integer>());
						System.out.println("CHECKED" + c.getFile_id() + " IS " + desired + " - " + degree);
						if(degree > desired){
							for(int i = degree; i > desired; i--){
								releaseChunk(c, new ArrayList<Integer>());
							}
						}
						else if(degree < desired){
							byte[] b = PeerFileManager.getChunkBody(c);
							if(b == null){
								System.out.println("BODY IS NULL");
							}
							storeChunk(new NetworkChunk(c, PeerFileManager.getChunkBody(c)), new ArrayList<Integer>());
						}
					}
				}	catch (RemoteException e) {
					continue;
				}
				catch (Exception e){
					e.printStackTrace();
				}
			}
		}
	}

	/**
	* If this peer is chunk holder, and no other peers have chunk, upon revive, deletes chunk
	* Sets delete and then deletes
	* For higher consistency, could setDelete replication_degree times
	*/
	private void checkUnreachableRevive(){
		try{
		Set<Entry<Integer, HashMap<Integer, FileSystemChunk>>> s0 = chunk_map.entrySet();
		Set<Entry<Integer, FileSystemChunk>> s1 = null;
		for (Entry<Integer, HashMap<Integer, FileSystemChunk>> e0 : s0) {
			s1 = e0.getValue().entrySet();
			for (Entry<Integer, FileSystemChunk> e1 : s1) {
				FileSystemChunk c = e1.getValue();
				System.out.println("Checking " + c.getChunk_nr());
					//does not delete chunks with desired degree equal to 1
					if(c.getDesiredDegree() == 1){
						continue;
					}
					Pair<Integer, PeerInterface> p = lookup(c.getKey());
					p = p.second.getChunkHolder(c.getFile_id(), c.getChunk_nr());
					if(p == null){
						if(c.getDelete()){
							deleteChunk(c);
						}
						else{
							c.setDelete(true);
						}
						continue;
					}
					if(p.first == guid){
						p = getSuccessor();
						if(p.first == guid || p.second.checkChunk(c, new ArrayList<Integer>()) == 0){
							if(c.getDelete()){
								deleteChunk(c);
							}
							else{
								c.setDelete(true);
							}
						}
						else{
							c.setDelete(false);
						}
					}
				}
			}
		}
		catch(RemoteException e){
		}
		catch(Exception e){
		}
	}

	/**
	* checks unreachable chunks
	* if the peer has a chunk that is unknown to successor and predecessor, peer deletes chunk
	*/
	private void checkUnreachableChunks(){
		Set<Entry<Integer, HashMap<Integer, FileSystemChunk>>> s0 = chunk_map.entrySet();
		Set<Entry<Integer, FileSystemChunk>> s1 = null;
		for (Entry<Integer, HashMap<Integer, FileSystemChunk>> e0 : s0) {
			s1 = e0.getValue().entrySet();
			for (Entry<Integer, FileSystemChunk> e1 : s1) {
				FileSystemChunk c = e1.getValue();
				try{
					//does not delete chunks with desired degree equal to 1
					if(c.getDesiredDegree() == 1){
						continue;
					}
					if(c.getDelete()){
						deleteChunk(c);
						continue;
					}
					Pair<Integer, PeerInterface> p = lookup(c.getKey());
					p = p.second.getChunkHolder(c.getFile_id(), c.getChunk_nr());
					//if this peer is not the chunk holder, and its predecessor does not know of the chunk, set the chunk to be deleted and then delete
					//if I am not chunk holder and delete chunk when I shouldn't, I will store the chunk again, after the chord balances
					if(p == null || p.first != guid){
						if(predecessor == null){
							deleteChunk(c);
						}
						else{
							if(predecessor.second.checkChunk(c, new ArrayList<Integer>()) == 0){
								deleteChunk(c);
							}
						}
					}
				}
				catch(RemoteException e){
					continue;
				}

			}
		}
	}

	/**
	* Checks chunk replication, also sets last check
	*/
	public int checkChunk(FileSystemChunk c, ArrayList<Integer> l) throws RemoteException{
		if(l.contains(guid)){
			return 0;
		}
		HashMap<Integer, FileSystemChunk> file_map = chunk_map.get(c.getFile_id());
		if (file_map == null) {
			return 0;
		}
		FileSystemChunk f = file_map.get(c.getChunk_nr());
		l.add(guid);
		if (f != null) {
			f.setLastCheck(new Date().getTime());
			if (f.getStatus() == 1) {
				return getSuccessor().second.checkChunk(c, l) + 1;
			}
			else if(f.getStatus() == 0){
				return getSuccessor().second.checkChunk(c, l);
			}
		}
		return 0;
	}

	/*
	* need to ask successor for files
	* the last peer having files can free them
	* predecessor becomes the node that we are asking to join
	*/
	public void join(int k, PeerInterface p) throws RemoteException, Exception {
		Pair<Integer, PeerInterface> successor = null;
		try {
			successor = p.lookup(k);
		} catch (RemoteException e) {
			throw new RemoteException("Unable to connect to Peer " + p);
		}
		if (successor.second == null) {
			throw new Exception("The specified Chord to join has a maximum of " + successor.first + " peers.");
		}
		if (successor.first == k) {
			throw new Exception("Peer with key " + k + " already exists.");
		}
		n = successor.second.getMaxPeers();
		guid = k;
		System.out.println("Looked up key " + k + " and received Peer " + successor.first);
		try {
			createRMI();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RemoteException("Unable to bind PeerInterface to RMI register.");
		}
		setSuccessor(successor);
		setFingerTable();
	}

	public int getMaxPeers() throws RemoteException {
		return n;
	}

	/**
	* Executes stabilize, fixFingers and checkPredecessor on different threads on a given delay
	*/
	public void start() {
		ScheduledExecutorService stabilize = Executors.newSingleThreadScheduledExecutor();
		stabilize.scheduleAtFixedRate(new Runnable() {
			public void run() {
				try{
					stabilize();
				}
				catch(Exception e){

				}
			}
		}, 0, Constants.STABILIZE_DELAY, TimeUnit.MILLISECONDS);

		ScheduledExecutorService fix_fingers = Executors.newSingleThreadScheduledExecutor();
		fix_fingers.scheduleAtFixedRate(new Runnable() {
			public void run() {
				fixFingers();
			}
		}, 0, Constants.FIX_FINGERS_DELAY, TimeUnit.MILLISECONDS);

		ScheduledExecutorService check_predecessor = Executors.newSingleThreadScheduledExecutor();
		check_predecessor.scheduleAtFixedRate(new Runnable() {
			public void run() {
				checkPredecessor();
			}
		}, 0, Constants.CHECK_PRED_DELAY, TimeUnit.MILLISECONDS);

		Thread check_revive = new Thread(){
			public void run(){
				checkUnreachableRevive();
				try {
					Thread.sleep(Constants.CHECK_UNREACH_REVIVE);
				} catch (InterruptedException e) {
				}
			}
		};
		try{
			check_revive.run();
			check_revive.join();
			//double check for setDelete and checkDelete
			check_revive.run();
			check_revive.join();
		}
		catch(InterruptedException e){
			System.out.println("Unable to properly revive. \nStopping execution.");
			return;
		}
		revived = true;

		ScheduledExecutorService check_replications = Executors.newSingleThreadScheduledExecutor();
		check_replications.scheduleAtFixedRate(new Runnable() {
			public void run() {
				try{
					checkChunkReplication();
				}
				catch(Exception e){
					System.out.println("CAUGHT CHECKCHUNK EXCEPTION");
				}
			}
		}, Constants.INIT_CHECK_REP_DELAY, Constants.CHECK_REP_DELAY, TimeUnit.SECONDS);

		ScheduledExecutorService check_unreachable = Executors.newSingleThreadScheduledExecutor();
		check_unreachable.scheduleAtFixedRate(new Runnable() {
			public void run() {
				try{
					checkUnreachableChunks();
				}
				catch(Exception e){

				}
			}
		}, Constants.INIT_CHECK_REP_DELAY, Constants.CHECK_REP_DELAY, TimeUnit.SECONDS);
	}

	/**
	* releases chunk, makes a recursive successor call to order the last peer holding the chunk to release it
	* receives a FileSystemChunk because there's no need to send body
	* also sets last_check
	* release chunk returns 1 if the chunk was actually deleted, 0 if not, so that other peers do not decrement their rep degree
	*/
	public int releaseChunk(FileSystemChunk c, ArrayList<Integer> l ) throws RemoteException {
		if(l.contains(guid)){
			return 0;
		}
		l.add(guid);
		HashMap<Integer, FileSystemChunk> file_map = chunk_map.get(c.getFile_id());
		System.out.println("RELEASING");
		if (file_map == null) {
			return 0;
		}
		FileSystemChunk f = file_map.get(c.getChunk_nr());
		if (f == null) {
			return 0;
		}
		f.setLastCheck(new Date().getTime());
		if (f.getStatus() == 0) {
			return getSuccessor().second.releaseChunk(c, l);
		}
		int d = c.getRepDegree();
		System.out.println("CHUNK " + c.getFile_id() + " is " + d);
		f.setRepDegree(d);
		c.setRepDegree(d - 1);
		if (d == 1) {
			deleteChunk(f);
			return 1;
		} else {
			return getSuccessor().second.releaseChunk(c, l);
		}
	}

	public ArrayList<FileSystemChunk> getChunksNewPred(int k){
		ArrayList<FileSystemChunk> r = new ArrayList<FileSystemChunk>();
		Set<Entry<Integer, HashMap<Integer, FileSystemChunk>>> s0 = chunk_map.entrySet();
		Set<Entry<Integer, FileSystemChunk>> s1 = null;
		for (Entry<Integer, HashMap<Integer, FileSystemChunk>> e0 : s0) {
			s1 = e0.getValue().entrySet();
			for (Entry<Integer, FileSystemChunk> e1 : s1) {
				FileSystemChunk c = e1.getValue();
				//checks if chunk belongs to new successor
				if (Utils.lookupUtil(guid, c.getKey(), k)) {
					r.add(c);
				}
			}
		}
		return r;
	}

	/**
	* Called on node joins and departures
	*/
	public int putChunk(NetworkChunk c) throws RemoteException {
		HashMap<Integer, FileSystemChunk> file_map = chunk_map.get(c.getFile_id());
		if (file_map == null) {
			file_map = new HashMap<Integer, FileSystemChunk>();
			chunk_map.put(c.getFile_id(), file_map);
		}
		FileSystemChunk f = file_map.get(c.getChunk_nr());
		if (f != null) {
			if (f.getStatus() == 1) {
				//already stored chunk
				return 1;
			}
		}
		byte[] b = c.getBody();
		if(b == null){
			//the chunk came with no body, needs to get it from the network
			Pair<Integer, PeerInterface> p = lookup(c.getKey());
			p = p.second.getChunkHolder(c.getFile_id(), c.getChunk_nr());
			if(p == null){
				System.out.println("Can't find chunk body, deleting it");
				deleteChunk(c.getFile_id(), c.getChunk_nr());
				return -1;
			}
			c = p.second.getChunk(c.getFile_id(), c.getChunk_nr());
			b = c.getBody();
			if(b == null){
				System.out.println("Can't find chunk body, deleting it");
				deleteChunk(c.getFile_id(), c.getChunk_nr());
				return -1;
			}
		}
		if (available_space_bytes < b.length) {
			f.setStatus(0);
			PeerFileManager.setChunkInfo(f);
			file_map.put(f.getChunk_nr(), f);
			return 0;
		}
		file_map.put(f.getChunk_nr(), f);
		f.setStatus(1);
		PeerFileManager.setChunkInfo(f);
		PeerFileManager.setChunkBody(f, b);
		available_space_bytes -= b.length;
		System.out.println("Got chunk " + c.getFile_id() + " - " + c.getChunk_nr());
		return 1;
	}


	/**
	* @param chunk
	* @param message_time
	* @return replication degree of the chunk
	* @throws RemoteException
	*/
	@Override
	public int storeChunk(NetworkChunk chunk, ArrayList<Integer> l) throws RemoteException {
		System.out.println("ASKED TO Store chunk nr "+chunk.getChunk_nr()+" from file "+chunk.getFile_id());
		HashMap<Integer, FileSystemChunk> file_map = chunk_map.get(chunk.getFile_id());
		if (file_map == null) {
			file_map = new HashMap<Integer, FileSystemChunk>();
			chunk_map.put(chunk.getFile_id(), file_map);
		}
		FileSystemChunk c = file_map.get(chunk.getChunk_nr());
		if (c != null) {
			if (l.contains(guid)) { // this message has given a full lap
				if (c.getRepDegree() == chunk.getRepDegree()) {
					c.setStatus(-1);
					// reply to client that nobody stored the chunk
					return 0;
				}
				// reply to client that chunk was stored with rep degree below desired
				return 0;
			}
			c.setStatus(1); // assume we are storing the chunk
		} else {
			c = new FileSystemChunk(chunk, 1);
		}
		l.add(guid);
		if (available_space_bytes < chunk.getBody().length) { // no space available
			c.setStatus(0);
			file_map.put(chunk.getChunk_nr(), c);
			// reply to previous peer number of stores in my sucessors
			return getSuccessor().second.storeChunk(chunk, l);
		}
		file_map.put(chunk.getChunk_nr(), c);
		PeerFileManager.setChunkInfo(c);
		PeerFileManager.setChunkBody(c, chunk.getBody());
		available_space_bytes -= chunk.getBody().length;
		int rep_degree = chunk.getRepDegree();
		System.out.println("Stored chunk from file id " + chunk.getFile_id() + ", rep degree is " + rep_degree);
		if (rep_degree > 1) {
			chunk.setRepDegree(rep_degree - 1);
			// reply to previous peer number of stores in my sucessors plus mine
			System.out.println("Asking " + getSuccessor().first + " to store");
			return 1 + getSuccessor().second.storeChunk(chunk, l);
		}
		// reply to previous peer that chunk was stored here
		return rep_degree;
	}

	public NetworkChunk getChunk(int file_id, int chunk_nr) throws RemoteException{
		HashMap<Integer, FileSystemChunk> m = chunk_map.get(file_id);
		if(m == null){
			return null;
		}
		FileSystemChunk f = m.get(chunk_nr);
		if(f == null){
			return null;
		}
		return new NetworkChunk(f, PeerFileManager.getChunkBody(f));
	}

	/**
	* Retrieves PeerInterface for peer that has the chunk, to prevent chunks from traversing the whole recursive call.
	*/
	public Pair<Integer, PeerInterface> getChunkHolder(int file_id, int chunk_nr) throws RemoteException {
		System.out.println("GET CHUNK HOLDER FILE_ID: "+file_id+" CHUNK NR: "+chunk_nr);
		FileSystemChunk f = null;
		try{
			f = chunk_map.get(file_id).get(chunk_nr);
		}
		catch(Exception e){
			return null;
		}
		if(f == null){
			return null;
		}
		int chunk_status = f.getStatus();
		if (chunk_status == -1)  // nobody stored this chunk
		return null;
		else if (chunk_status == 0) // some successor stored the chunk, but not me
		return getSuccessor().second.getChunkHolder(file_id, chunk_nr);
		return pair;
	}

	private void deleteChunk(FileSystemChunk chunk) {
		PeerFileManager.deleteChunk(chunk);
		int file_id = chunk.getFile_id();
		chunk_map.get(file_id).remove(chunk.getChunk_nr());
		if (chunk_map.get(file_id).isEmpty()) chunk_map.remove(file_id);
	}

	@Override
	public void deleteChunk(int file_id, int chunk_nr) throws RemoteException {
		HashMap<Integer, FileSystemChunk> m = chunk_map.get(file_id);
		if(m == null){
			return;
		}
		FileSystemChunk chunk = m.get(chunk_nr);
		if(chunk == null){
			return;
		}
		int chunk_status = chunk.getStatus();
		if (chunk_status == -1)  // nobody stored this chunk
		return;
		deleteChunk(chunk);
		getSuccessor().second.deleteChunk(file_id, chunk_nr);
	}

}
