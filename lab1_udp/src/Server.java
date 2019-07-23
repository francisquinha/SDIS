import java.io.*;
import java.net.*;
import java.util.regex.*;
import java.util.HashMap;

public class Server {
    public static void main(String args[]) {
        int portNumber = Integer.parseInt(args[0]);
        DatagramSocket serverSocket;
        try {
            serverSocket = new DatagramSocket(portNumber);
            serverSocket.setSoTimeout(120000); // timeout after 2 min without any packet
        }
        catch (SocketException e) {
            System.out.println("ERROR OPENING SOCKET");
            return;
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            System.out.println("SERVER HOSTNAME: " + hostname);
        }
        catch (UnknownHostException e) {
            System.out.println("ERROR GETTING HOSTNAME");
            return;
        }
        String patternString = "[A-Z0-9]{2}-[A-Z0-9]{2}-[A-Z0-9]{2}";
        Pattern pattern = Pattern.compile(patternString);
        HashMap<String, String> hashMap = new HashMap<>();
        boolean stop = false;
        while(!stop) {
            byte[] receiveData = new byte[274];
            try {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                String sentence = new String(receivePacket.getData());
                InetAddress IPAddress = receivePacket.getAddress();
                int port = receivePacket.getPort();
                String result;
                if (sentence.startsWith("REGISTER")) {
                    result = register(pattern, hashMap, sentence);
                } else if (sentence.startsWith("LOOKUP")) {
                    result = lookup(hashMap, sentence);
                } else if (sentence.startsWith("STOP")) {
                    System.out.println("CLOSING SERVER");
                    result = "SERVER CLOSED";
                    stop = true;
                } else {
                    result = "ERROR";
                    System.out.println(result);
                }
                byte[] sendData = result.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, port);
                serverSocket.send(sendPacket);
            } catch (SocketTimeoutException e) {
                System.out.println("CLOSING SERVER DUE TO TIMEOUT");
                stop = true;
            } catch (IOException e) {
                System.out.println("CLOSING SERVER DUE TO RECEIVE OR SEND ERROR");
                stop = true;
            }
        }
        serverSocket.close();
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
