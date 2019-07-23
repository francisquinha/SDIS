import java.io.*;
import java.net.*;

public class Client {

    public static void main(String args[]) {
        String multicastMessage = getMulticastMessage(args);
        if (multicastMessage == null) return;
        DatagramPacket sendPacket = getPacket(args, multicastMessage);
        if (sendPacket == null) return;
        sendPacket(sendPacket);
    }

    private static String getMulticastMessage(String args[]) {
        String multicastIP = args[0];
        int multicastPort = Integer.parseInt(args[1]);
        MulticastSocket multicastSocket;
        InetAddress multicastAddress;
        try {
            multicastSocket = new MulticastSocket(multicastPort);
            multicastSocket.setSoTimeout(4000); // timeout after 4 seconds without a reply from server
            multicastAddress = InetAddress.getByName(multicastIP);
            multicastSocket.joinGroup(multicastAddress);
        } catch (UnknownHostException e) {
            System.out.println("ERROR GETTING HOSTNAME");
            return null;
        } catch (IOException e) {
            System.out.println("ERROR OPENING MULTICAST SOCKET OR JOINING MULTICAST GROUP");
            return null;
        }
        byte[] multicastData = new byte[20];
        DatagramPacket multicastPacket = new DatagramPacket(multicastData, multicastData.length);
        try {
            multicastSocket.receive(multicastPacket);
        } catch (IOException e) {
            System.out.println("ERROR RECEIVING MULTICAST PACKET");
            return null;
        }
        multicastSocket.close();
        return new String(multicastData, 0, multicastData.length);
    }

    private static DatagramPacket getPacket(String[] args, String multicastMessage) {
        String serviceIP = multicastMessage.substring(0, multicastMessage.indexOf(" "));
        InetAddress serviceAddress;
        try {
            serviceAddress = InetAddress.getByName(serviceIP);
        } catch (UnknownHostException e) {
            System.out.println("ERROR GETTING SERVICE HOSTNAME");
            return null;
        }
        int servicePort = Integer.parseInt(multicastMessage.substring(multicastMessage.indexOf(" ") + 1).trim());
        String sentence = "";
        if (args.length > 1) {
            for (int i = 2; i < args.length - 1; i++) {
                sentence += args[i] + " ";
            }
            sentence += args[args.length - 1];
        }
        byte[] sendData = sentence.getBytes();
        return new DatagramPacket(sendData, sendData.length, serviceAddress, servicePort);
    }

    private static void sendPacket(DatagramPacket sendPacket) {
        DatagramSocket serviceSocket;
        try {
            serviceSocket = new DatagramSocket();
            serviceSocket.setSoTimeout(4000); // timeout after 4 seconds without a reply from server
        }
        catch (SocketException e) {
            System.out.println("ERROR OPENING SERVICE SOCKET");
            return;
        }
        byte[] receiveData = new byte[266];
        try {
            serviceSocket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serviceSocket.receive(receivePacket);
            String result = new String(receivePacket.getData());
            System.out.println(result);
        }
        catch (SocketTimeoutException e) {
            System.out.println("SERVER TIMEOUT");
        } catch (IOException e) {
            System.out.println("SEND OR RECEIVE ERROR");
        }
        serviceSocket.close();
    }

}