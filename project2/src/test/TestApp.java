package test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.util.Arrays;

import utils.Constants;
import client.MyFileHandler;
import client.TestAppInterface;

public class TestApp {
	private static final int MIN_ARGS = 3;
	private static final int MAX_PROTOCOL_ARGS = 4;
	private static final int MAX_ARGS = 5;
	private static final String REGISTER = "REGISTER";
	private static final String LOGIN = "LOGIN";

	public static void main(String[] args) throws InterruptedException {
		if (args.length < MIN_ARGS || args.length > MAX_ARGS) {
			System.out
					.println("Invalid invokation. TestApp must be invoked as follows"
							+ ":\njava TestApp (<IP address>:)?<remote_object_name> <sub_protocol> <opnd_1> (<opnd_2>)?\n"
							+ "OR\n"
							+ "java TestApp <action> <username> <password> <acess_point> <acess_point_key>\n"
							+ "\t\tWhere action is REGISTER or LOGIN");
			return;
		}

		String protocol = null;
		String username = null;
		String password = null;
		InetAddress access_point = null;
		int access_point_key = 0;
		String[] peer_access_point = null;
		String address = null;
		String remote_object_name = null;
		Registry registry = null;
		TestAppInterface test_app_interface = null;

		if (args[0].equals(REGISTER) || args[0].equals(LOGIN)) {
			try {
				registry = LocateRegistry.getRegistry();
			} catch (RemoteException r) {
				System.out
						.println("Couldn't connect to registry.\nStopping execution...");
				return;
			}

			try {
				test_app_interface = (TestAppInterface) registry
						.lookup(Constants.REQUEST_HANDLER_REMOTE_NAME);
			} catch (Exception e) {
				System.out.println("Couldn't lookup for remote object "
						+ Constants.REQUEST_HANDLER_REMOTE_NAME
						+ ".\nStopping execution..");
				return;
			}
			try {
				username = args[1];
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			try {
				password = args[2];
			} catch (Exception exc) {
				exc.printStackTrace();
			}
			try {
				access_point = InetAddress.getByName(args[3]);
			} catch (UnknownHostException u) {
				System.out.println("Unknown host.\n");
			}
			try {
				access_point_key = Integer.parseInt(args[4]);
			} catch (NumberFormatException n) {
				System.out.println("Access point key is not a number.\n");
			}

			try {
				if (args[0].equals(REGISTER)) {
					test_app_interface.register(username, password,
							access_point, access_point_key);
					System.out.println("REGISTERED");
				} else if (args[0].equals(LOGIN)) {
					test_app_interface.login(username, password, access_point,
							access_point_key);
					System.out.println("LOGGED IN");
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		else {
			try {
				peer_access_point = args[0].split(":");
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (peer_access_point.length > 2) {
				System.out
						.println("Invalid peer acess point.\nStopping execution...");
				return;
			} else {
				switch (peer_access_point.length) {
				case 2:
					address = peer_access_point[0];
					remote_object_name = peer_access_point[1];
				case 1:
					address = Constants.LOCALHOST;
					remote_object_name = peer_access_point[0];
				}
			}

			/*try {
				registry = LocateRegistry.getRegistry(address);
			} catch (RemoteException r) {
				System.out.println("Couldn't connect to registry at " + address
						+ "\nStopping execution...");
				return;
			}*/

			try {
				test_app_interface = (TestAppInterface) Naming.lookup("//" + address + ":1099/" + Constants.REQUEST_HANDLER_REMOTE_NAME);
			} catch (Exception e) {
				System.out.println("Couldn't lookup for remote object "
						+ remote_object_name + ".\nStopping execution..");
				e.printStackTrace();
				return;
			}

			try {
				protocol = args[1];
			} catch (Exception e) {
				System.out.println("Protocol hasn't been specified");
				return;
			}

			if (!Arrays.asList(Constants.PROTOCOLS).contains(protocol)) {
				System.out
						.print("Invalid Protocol. Use one of the available protocols:\n{");
				int i = 0;
				for (; i < Constants.PROTOCOLS.length - 1; i++) {
					System.out.print(Constants.PROTOCOLS[i] + ", ");
				}
				System.out.println(Constants.PROTOCOLS[i + 1]
						+ "}\nStopping execution.");
				return;
			}

			MyFileHandler file = null;
			int new_peer_size = 0;
			String filename = args[2];
			if (protocol.equals("RECLAIM")) {
				boolean size_error = false;
				try {
					new_peer_size = Integer.parseInt(args[2]);
					if (new_peer_size <= 0) {
						size_error = true;
					}
				} catch (NumberFormatException e) {
					size_error = true;
				}
				if (size_error) {
					System.out
							.println(args[2]
									+ " is not a valid size number to reclaim.\nStopping execution.");
					return;
				}
			} else if ((!protocol.equals("RESTORE"))) {
				try {
					if (!(file = new MyFileHandler(args[2])).isFile()) {
						System.out
								.println(args[2]
										+ " is not a valid path to a file.\nStopping execution.");
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				filename = args[2];
			}
			int replication_degree = 0;
			if (protocol.equals("BACKUP")) {
				if (args.length == MAX_PROTOCOL_ARGS) {
					if ((replication_degree = Integer.parseInt(args[3])) <= 0) {
						System.out.println("Invalid replication degree: "
								+ args[3] + ".\nStopping execution.");
						return;
					}
				} else {
					System.out.println("BACKUP protocols require "
							+ MAX_PROTOCOL_ARGS
							+ " arguments.\nStopping execution.");
					return;
				}
			} else {
				if (args.length == MAX_PROTOCOL_ARGS) {
					System.out.println("Non BACKUP protocols require "
							+ MIN_ARGS + " arguments.\nStopping execution.");
					return;
				}
			}
			try {
				switch (protocol) {
				case "BACKUP":
					int d = test_app_interface.backup(filename, replication_degree);
					System.out.println("Saved file on peer with name " + filename + ".\nPlease use this name, on this peer, to restore the file.");
					System.out.println("Rep degree " + d);
					break;
				case "RESTORE":
					test_app_interface.restore(filename);
					System.out.println("File " + filename + "restored successfully.\n");
					break;
				case "DELETE":
					test_app_interface.delete(filename);
					System.out.println("File " + filename + "deleted successfully.\n");
					break;
				}
			} catch (Exception e) {
				System.out.println("Error invoking protocol " + protocol + ". "
						+ e.getMessage() + ".\nStopping execution.");
			}
		}

	}
}
