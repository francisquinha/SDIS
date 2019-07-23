import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

class TestApp extends Thread {

    private static final int PACKET_SIZE = 65535;
    private static DatagramSocket socket;

    public static void main (String[] args) {
        InetAddress peerAddress;
        int peerPort;
        try {
            String[] peerAP = args[0].split(":");
            if (peerAP.length > 2) {
                howTo();
                return;
            }
            if (peerAP.length == 2 && peerAP[0].length() > 0) {
                peerAddress = InetAddress.getByName(peerAP[0]);
            } else peerAddress = InetAddress.getLocalHost();
            peerPort = Integer.parseInt(peerAP[peerAP.length - 1]);
            String sentence = "";
            if (args.length > 1) {
                for (int i = 1; i < args.length - 1; i++) {
                    sentence += args[i] + " ";
                }
                sentence += args[args.length - 1];
            }
            byte[] sendData = sentence.getBytes();
            TestApp testApp = new TestApp();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, peerAddress, peerPort);
            socket.send(sendPacket);
            testApp.start();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            try {
                buffer.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket.close();
        }
        catch(ArrayIndexOutOfBoundsException | NumberFormatException | IOException e) {
            howTo();
        }
    }

    private static void howTo() {
        System.out.println("TestApp must be invoked as follows: java TestApp <peerAP> <subProtocol> <opnd1> <opnd2>:");
        System.out.println("peerAP is the peer access point and must be in one of formats ipAddress:portNumber, " +
                "or :portNumber, or portNumber;");
        System.out.println("subProtocol must be BACKUP, RESTORE, DELETE or RECLAIM;");
        System.out.println("opnd1 is the path of the file in case of BACKUP, RESTORE and DELETE " +
                "or the amount of space to RECLAIM;");
        System.out.println("opnd2 is only used for BACKUP and is the desired replication degree.");
    }

    private TestApp() {
        try {
            socket = new DatagramSocket();
        } catch (SocketException e) {
            socket = null;
        }
    }

    public void run() {
        byte[] receiveData = new byte[PACKET_SIZE];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        try {
            socket.receive(receivePacket);
        } catch (IOException e) {
            System.out.println("Error receiving packet.");
            return;
        }
        System.out.println(new String(receivePacket.getData()));
    }

}
