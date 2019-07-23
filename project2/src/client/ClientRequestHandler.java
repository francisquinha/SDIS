package client;

import peer.PeerInterface;
import utils.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

public class ClientRequestHandler implements TestAppInterface {

	String username = null;
	String password = null;
	SecretKey userkey = null;
	InetAddress access_point = null;
	int access_point_key = 0;
	PeerInterface access_point_interface;
	static Registry registry;

	private static final int MIN_ARGS = 2;
	private static final int MAX_ARGS = 4;
	private static final String STOP = "stop";

	public void start() throws Exception {
		Thread shutdown_thread = new Thread() {
			public void run() {
				System.out
						.println("\nStopping execution in response to shutdown hook.");
				if(!Constants.LAN_MODE){
				PortForwarding.shutdown();
				}
				System.out.println("Shutdown hook complete");
			}
		};
		Runtime.getRuntime().addShutdownHook(shutdown_thread);
		if(!Constants.LAN_MODE){
		try {
			PortForwarding.doPortForwarding();
		} catch (Exception e) {
			System.err.println("Port forwarding error");
		}
		}
		readFromConsole();
		return;
	}

	/**
	 * Generates client encryption key based on username and password provided
	 *
	 * @param username
	 * @param password
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws NoSuchAlgorithmException
	 */
	public SecretKeySpec generateKey(String username, String password)
			throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] key_gen = (username + password).getBytes("UTF-8");
		MessageDigest sha = MessageDigest.getInstance("SHA-256");
		key_gen = sha.digest(key_gen);
		key_gen = Arrays.copyOf(key_gen, 16); // use only first 128 bit
		return new SecretKeySpec(key_gen, "AES");
	}

	/**
	 * encrypts ClientMetaData object
	 *
	 * @param data
	 * @param key
	 * @return
	 */
	public static SealedObject encrypt_metadata(ClientMetaData data,
			SecretKey key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, key);
			return new SealedObject(data, cipher);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * decrypts SealedObject into ClientMetaData
	 *
	 * @param s
	 * @param key
	 * @return
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws IOException
	 * @throws BadPaddingException
	 * @throws IllegalBlockSizeException
	 * @throws ClassNotFoundException
	 */
	public static ClientMetaData decrypt_metadata(SealedObject s, SecretKey key)
			throws NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, ClassNotFoundException,
			IllegalBlockSizeException, BadPaddingException, IOException {
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(Cipher.DECRYPT_MODE, key);
		return (ClientMetaData) s.getObject(cipher);
	}

	private NetworkChunk getChunk(int i, int n, PeerInterface p) {
		try {
			Pair<Integer, PeerInterface> h = p.getChunkHolder(i, n);
			if (h == null) {
				return null;
			}
			return h.second.getChunk(i, n);
		} catch (RemoteException e) {
			return null;
		}
	}

	@Override
	public ClientMetaData register(String username, String password,
			InetAddress access_point, int access_point_key) throws Exception {
		userkey = generateKey(username, password);
		this.username = username;
		this.password = password;
		this.access_point = access_point;
		this.access_point_key = access_point_key;
if(!Constants.LAN_MODE){
		Properties props = System.getProperties();
		props.setProperty("java.rmi.server.hostname",
				access_point.getHostAddress());
}
		System.out.println("LOOKUP ACCESS PEER " + "//"
				+ access_point.getHostAddress() + ":1099/"
				+ Integer.toString(access_point_key));
		this.access_point_interface = (PeerInterface) Naming.lookup("//"
				+ access_point.getHostAddress() + ":1099/"
				+ Integer.toString(access_point_key));

		int user_metadata_id = Utils.generateFileId(username);
		System.out.println("TRYING GET MAX PEERS");
		int user_metadata_key = user_metadata_id
				% access_point_interface.getMaxPeers();
		System.out.println("MAX PEERS COMPLETED");
		PeerInterface responsible_node = access_point_interface
				.lookup(user_metadata_key).second;
		NetworkChunk exists = getChunk(user_metadata_id, 0, responsible_node);
		if (exists != null) {
			throw new Exception("Username already exists in the network");
		} else {
			ClientMetaData metadata = new ClientMetaData();
			SealedObject metadata_encrypted = encrypt_metadata(metadata,
					userkey);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(bos);
			out.writeObject(metadata_encrypted);
			NetworkChunk meta_chunk = new NetworkChunk(0, user_metadata_id, 3,
					3, user_metadata_key, bos.toByteArray());
			responsible_node.storeChunk(meta_chunk, new ArrayList<Integer>());
			return metadata;
		}
	}

	@Override
	public ClientMetaData login(String username, String password,
			InetAddress access_point, int access_point_key) throws Exception {
		userkey = generateKey(username, password);
		this.username = username;
		this.password = password;
		this.access_point = access_point;
		this.access_point_key = access_point_key;
if(!Constants.LAN_MODE){
		Properties props = System.getProperties();
		props.setProperty("java.rmi.server.hostname",
				access_point.getHostAddress());
}
		access_point_interface = (PeerInterface) Naming.lookup("//"
				+ access_point.getHostAddress() + ":1099/"
				+ Integer.toString(access_point_key));

		int user_metadata_id = Utils.generateFileId(username);

		int user_metadata_key = user_metadata_id
				% access_point_interface.getMaxPeers();
		PeerInterface responsible_node = access_point_interface
				.lookup(user_metadata_key).second;
		NetworkChunk metadata_chunk = getChunk(user_metadata_id, 0,
				responsible_node);
		if (metadata_chunk == null) {
			throw new Exception("Metadata file not available on network");
		}
		ByteArrayInputStream bis = new ByteArrayInputStream(
				metadata_chunk.getBody());
		ObjectInputStream in = new ObjectInputStream(bis);
		SealedObject metadata_encrypted = (SealedObject) in.readObject();
		try {
			return decrypt_metadata(metadata_encrypted, userkey);
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage()
					+ "\n Username or password not correct.");
		}

		return null;
	}

	/**
	 *
	 * @param file_path
	 * @param rep_degree
	 * @return minimum replication degree of all file chunks
	 * @throws Exception
	 */
	@Override
	public int backup(String file_path, int rep_degree) throws Exception {
		ClientMetaData user_metadata = getUserMetadata();
		MyFileHandler fh = null;
		try {
			fh = new MyFileHandler(file_path);
		} catch (Exception e) {
			return -1;
		}
		int chunk_id;
		int max_peers = access_point_interface.getMaxPeers();
		chunk_id = fh.getFileId();
		System.out.println("FILENAME: " + fh.getName() + " FILE ID: "
				+ chunk_id);
		chunk_id %= max_peers;
		NetworkChunk to_send = fh.nextChunk(rep_degree, chunk_id, userkey);
		int nr_file_chunks = 0;
		int final_rep_degree = rep_degree;
		int chunk_rep_degree;
		while (to_send != null) {
			PeerInterface responsible_node = access_point_interface
					.lookup(chunk_id).second;
			System.out.println("Asking store chunk nr " + to_send.getChunk_nr()
					+ " from file " + to_send.getFile_id() + " to peer "
					+ chunk_id);
			chunk_rep_degree = responsible_node.storeChunk(to_send,
					new ArrayList<Integer>());
			if (chunk_rep_degree < final_rep_degree)
				final_rep_degree = chunk_rep_degree;
			nr_file_chunks = to_send.getChunk_nr();
			chunk_id = (chunk_id + 1) % max_peers;
			to_send = fh.nextChunk(rep_degree, chunk_id, userkey);
		}
		System.out.println("Saved file " + fh.getFileId());
		user_metadata.addFile(fh.getName(), fh.getFileId(), nr_file_chunks);
		setUserMetadata(user_metadata);
		System.out.println("Returning " + fh.getName());
		return final_rep_degree;
	}

	private ClientMetaData getUserMetadata() throws Exception {
		int user_metadata_id = Utils.generateFileId(username);
		int user_metadata_key = user_metadata_id
				% access_point_interface.getMaxPeers();
		PeerInterface metadata_responsible_node = access_point_interface
				.lookup(user_metadata_key).second;
		NetworkChunk metadata_chunk = getChunk(user_metadata_id, 0,
				metadata_responsible_node);
		if (metadata_chunk == null) {
			throw new Exception("Metadata file not available on network");
		}
		ByteArrayInputStream bis = new ByteArrayInputStream(
				metadata_chunk.getBody());
		ObjectInputStream in = new ObjectInputStream(bis);
		SealedObject metadata_encrypted = (SealedObject) in.readObject();
		return decrypt_metadata(metadata_encrypted,
				generateKey(username, password));
	}

	private void setUserMetadata(ClientMetaData metadata) throws Exception {
		int user_metadata_id = Utils.generateFileId(username);
		int user_metadata_key = user_metadata_id
				% access_point_interface.getMaxPeers();
		PeerInterface responsible_node = access_point_interface
				.lookup(user_metadata_key).second;
		SecretKey k = generateKey(username, password);
		SealedObject metadata_encrypted = encrypt_metadata(metadata, k);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bos);
		out.writeObject(metadata_encrypted);
		NetworkChunk meta_chunk = new NetworkChunk(0, user_metadata_id, 3, 3,
				user_metadata_key, bos.toByteArray());
		responsible_node.storeChunk(meta_chunk, new ArrayList<Integer>());
	}

	@Override
	public void delete(String file_name) throws Exception {
		ClientMetaData user_metadata = getUserMetadata();
		FileMetaData file_metadata = user_metadata.getFileMetadata(file_name);
		int file_id = file_metadata.getFile_id();
		int nr_chunks = file_metadata.getNumber_chunks();
		int max_peers=access_point_interface.getMaxPeers();
		for (int i = 0; i <= nr_chunks; i++) {
			int chunk_id = file_id;
			chunk_id += i;
			chunk_id = chunk_id % max_peers;
			PeerInterface responsible_node = access_point_interface
					.lookup(chunk_id).second;
			try{
			System.out.println("BEFORE133212312");
			responsible_node.deleteChunk(file_id, i);
			System.out.println("HERE133212312");
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean restore(String file_name) throws Exception {
		System.out.println("RESTORE FILE_NAME: " + file_name);
		ClientMetaData user_metadata = getUserMetadata();
		FileMetaData file_metadata = user_metadata.getFileMetadata(file_name);
		int file_id = file_metadata.getFile_id();
		int nr_chunks = file_metadata.getNumber_chunks();
		System.out.println("FILE ID FROM METADATA: " + file_id + " CHUNK NRS: "
				+ nr_chunks);
		File verify_name = new File(file_name);
		int filename_append = 1;
		while (verify_name.exists()) {
			verify_name = new File(file_name + "(" + filename_append + ")");
			filename_append++;
		}
		System.out.println("Storing restored file to " + verify_name.getName());
		FileOutputStream fos = new FileOutputStream(verify_name);
		for (int i = 0; i <= nr_chunks; i++) {
			int chunk_id = file_id;
			chunk_id += i;
			chunk_id = chunk_id % access_point_interface.getMaxPeers();
			System.out.println("here");
			PeerInterface responsible_node = access_point_interface
					.lookup(chunk_id).second;
			NetworkChunk c = getChunk(file_id, i, responsible_node);
			if (c == null || c.getBody() == null) {
				fos.close();
				throw new Exception("Chunk nr " + i
						+ " not available. Aborting.");
			}
			fos.write(Utils.decrypt(c.getBody(), userkey));
		}
		fos.close();
		return true;
	}

	private void readFromConsole() {
		String[] arguments = null;
		String s = null;
		while (true) {
			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			System.out.print("Dear User, please enter the commands: \n");
			try {
				s = br.readLine();
			} catch (IOException io) {
				System.out.println("Cannot read line.\n");
				io.printStackTrace();
			}
			if (s.equals(STOP)) {
				break;
			}
			else{
				arguments = s.split(" ");
				test(arguments);
			}
		}
		return;
	}

	private void test(String args[]) {
		if (args.length < MIN_ARGS || args.length > MAX_ARGS) {
			System.out
					.println("Invalid invokation. ClientRequestHandler must be invoked as follows"
							+ ":\n<sub_protocol> <opnd_1> (<opnd_2>)? (<access_point>:<access_point_key>)?\n"
							+ "OR\n"
							+ "<action> <username> <password> <access_point>:<access_point_key>\n"
							+ "\t\tWhere action is REGISTER or LOGIN");
			return;
		}

		String protocol = args[0].toUpperCase();
		if (protocol.equals("REGISTER") || protocol.equals("LOGIN")) {
			String username = args[1];
			String password = args[2];
			String[] access_point = args[3].split(":");
			if (access_point.length != 2) {
				System.out
						.println("Access point specification must be <access_point>:<access_point_key>");
				return;
			}
			InetAddress access_address = null;
			int access_key;
			try {
				access_address = InetAddress.getByName(access_point[0]);
				access_key = Integer.parseInt(access_point[1]);
			} catch (UnknownHostException e) {
				System.out
						.println("Access point specification must be <access_point>:<access_point_key> where key is an integer");
				return;
			}
			if (protocol.equals("REGISTER")) {
				try {
					register(username, password, access_address, access_key);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (protocol.equals("LOGIN")) {
				try {
					login(username, password, access_address, access_key);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} else {
			if (protocol.equals("BACKUP")) {
				// <file_path> <rep_degree>
				String file_path = args[1];
				int rep_degree = 0;
				try {
					rep_degree = Integer.parseInt(args[2]);
				} catch (NumberFormatException e) {
					System.out
							.println("Replication degree must be number.\nBACKUP parameters: <file_path> <rep_degree>");
				}
				if (args.length == 4) {
					String[] access_point = args[3].split(":");
					InetAddress access_address = null;
					int access_key;
					try {
						access_address = InetAddress.getByName(access_point[0]);
						access_key = Integer.parseInt(access_point[1]);
					} catch (UnknownHostException e) {
						System.out
								.println("Access point specification must be <access_point>:<access_point_key> where key is an integer");
						return;
					}
					this.access_point = access_address;
					this.access_point_key = access_key;
					try {
						if(!Constants.LAN_MODE){
						Properties props = System.getProperties();
						props.setProperty("java.rmi.server.hostname",
								this.access_point.getHostAddress());
						}
						this.access_point_interface = (PeerInterface) Naming
								.lookup("//"
										+ this.access_point.getHostAddress()
										+ ":1099/"
										+ Integer
												.toString(this.access_point_key));
					} catch (MalformedURLException | RemoteException
							| NotBoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					backup(file_path, rep_degree);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else if(protocol.equals("RESTORE") || protocol.equals("DELETE")){
				String file_name = args[1];
				if (args.length == 3) {
					String[] access_point = args[2].split(":");
					InetAddress access_address = null;
					int access_key;
					try {
						access_address = InetAddress.getByName(access_point[0]);
						access_key = Integer.parseInt(access_point[1]);
					} catch (UnknownHostException e) {
						System.out
								.println("Access point specification must be <access_point>:<access_point_key> where key is an integer");
						return;
					}
					this.access_point = access_address;
					this.access_point_key = access_key;
					try {
						if(!Constants.LAN_MODE){
						Properties props = System.getProperties();
						props.setProperty("java.rmi.server.hostname",
								this.access_point.getHostAddress());
						}
						this.access_point_interface = (PeerInterface) Naming
								.lookup("//"
										+ this.access_point.getHostAddress()
										+ ":1099/"
										+ Integer
												.toString(this.access_point_key));
					} catch (MalformedURLException | RemoteException
							| NotBoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				try {
					if(protocol.equals("RESTORE")){
					restore(file_name);
					}
					if(protocol.equals("DELETE")){
						delete(file_name);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else{
				
			}
		}
	}

}
