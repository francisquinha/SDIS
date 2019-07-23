import java.io.*;
import java.net.*;

public class Client {
    public static void main(String args[]) {
        String host_name = args[0];
        int port_number = Integer.parseInt(args[1]);
        String sentence = "";
        if (args.length > 1) {
            for (int i = 2; i < args.length - 1; i++) {
                sentence += args[i] + " ";
            }
            sentence += args[args.length - 1];
        }
        DatagramSocket clientSocket;
        try {
            clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(4000); // timeout after 4 seconds without a reply from server
        }
        catch (SocketException e) {
            System.out.println("ERROR OPENING SOCKET");
            return;
        }
        InetAddress IPAddress = null;
        try {
            IPAddress = InetAddress.getByName(host_name);
        } catch (UnknownHostException e) {
            System.out.println("ERROR GETTING HOSTNAME");
            return;
        }
        byte[] receiveData = new byte[266];
        byte[] sendData = sentence.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port_number);
        try {
            clientSocket.send(sendPacket);
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            String result = new String(receivePacket.getData());
            System.out.println(result);
        }
        catch (SocketTimeoutException e) {
            System.out.println("SERVER TIMEOUT");
        } catch (IOException e) {
            System.out.println("SEND OR RECEIVE ERROR");
        }
        clientSocket.close();
    }
}