package client;

public class ClientStart {
	public static void main(String[] args) {
		ClientRequestHandler request = new ClientRequestHandler();
		try {
			request.start();
		} catch (Exception e) {
			System.out.println("Could not forward the request");
			e.printStackTrace();
		}
		Runtime.getRuntime().exit(0);
	}
}
