import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

public class Service extends Thread {

    private DatagramSocket socket;
    private boolean stop;
    private String serviceIP;
    private Pattern pattern;
    HashMap<String, String> hashMap = new HashMap<>();

    Service(int servicePort) {
        try {
            this.socket = new DatagramSocket(servicePort);
            this.socket.setSoTimeout(4000); // timeout after 4 seconds without any message from client
        }
        catch (SocketException e) {
            System.out.println("ERROR OPENING SERVICE SOCKET");
        }
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(InetAddress.getByName("google.pt"), 80));
            this.serviceIP = sock.getLocalAddress().getHostAddress();
            sock.close();
        } catch (IOException e) {
            System.out.println("ERROR GETTING SERVICE IP");
        }
        this.stop = false;
        this.pattern = Pattern.compile("[A-Z0-9]{2}-[A-Z0-9]{2}-[A-Z0-9]{2}");
        this.hashMap = new HashMap<>();
    }

    public void run() {
        while(!this.stop) {
            byte[] receiveData = new byte[274];
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                this.socket.receive(receivePacket);
                String sentence = new String(receivePacket.getData());
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();
                String result;
                if (sentence.startsWith("REGISTER")) {
                    result = register(pattern, hashMap, sentence);
                } else if (sentence.startsWith("LOOKUP")) {
                    result = lookup(hashMap, sentence);
                } else {
                    result = "ERROR";
                    System.out.println(result);
                }
                byte[] sendData = result.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                socket.send(sendPacket);
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                System.out.println("CLOSING SERVICE DUE TO RECEIVE OR SEND ERROR");
                this.stop = true;
            }
        }
        System.out.println("CLOSING SERVICE\n");
        this.socket.close();
    }

    public void cancel() {
        this.stop = true;
    }

    public String getServiceIP() {
        return this.serviceIP;
    }

    private static String register(Pattern pattern, HashMap<String, String> hashMap, String sentence) {
        String result;
        String plateNumber = sentence.substring(9, 17);
        Matcher matcher = pattern.matcher(plateNumber);
        if (matcher.find()) {
            String ownerName = sentence.substring(18);
            hashMap.put(plateNumber, ownerName);
            int size = hashMap.size();
            result = Integer.toString(size);
            System.out.println("REGISTER " + plateNumber + " " + ownerName + ": " + result);
        }
        else {
            result = "ERROR";
            System.out.println("ERROR");
        }
        return result;
    }

    private static String lookup(HashMap<String, String> hashMap, String sentence) {
        String result;
        String plateNumber = sentence.substring(7, 15);
        Object owner = hashMap.get(plateNumber);
        if (owner != null) {
            String ownerName = owner.toString();
            System.out.println("LOOKUP " + plateNumber + ": " + ownerName);
            result = plateNumber + " " + ownerName;
        }
        else {
            System.out.println("LOOKUP " + plateNumber + ": NOT FOUND");
            result = plateNumber + " NOT FOUND";
        }
        return result;
    }

}
