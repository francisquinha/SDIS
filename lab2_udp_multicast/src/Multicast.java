import java.io.*;
import java.net.*;

public class Multicast extends Thread {

    private DatagramSocket socket;
    private boolean stop;
    private String multicastIP;
    private int multicastPort;
    private String serviceIP;
    private int servicePort;
    private DatagramPacket packet;

    Multicast(int servicePort, String serviceIP, int multicastPort, String multicastIP) {
        this.servicePort = servicePort;
        this.serviceIP = serviceIP;
        this.multicastPort = multicastPort;
        this.multicastIP = multicastIP;
        InetAddress multicastAddress;
        try {
            this.socket = new DatagramSocket(multicastPort);
        }
        catch (SocketException e) {
            System.out.println("ERROR OPENING MULTICAST SOCKET");
            return;
        }
        try {
            multicastAddress = InetAddress.getByName(multicastIP);
        }
        catch (UnknownHostException e) {
            System.out.println("ERROR GETTING MULTICAST ADDRESS");
            socket.close();
            return;
        }
        String message = this.serviceIP + " " + servicePort;
        byte[] data = message.getBytes();
        this.packet = new DatagramPacket(data, data.length, multicastAddress, multicastPort);
    }

    public void run() {
        while (!this.stop) {
            try {
                socket.send(packet);
                System.out.println("MULTICAST: " + this.multicastIP + " " + this.multicastPort + ": "
                        + this.serviceIP + " " + this.servicePort);
                Thread.sleep(1000);
            } catch (IOException e) {
                System.out.println("CLOSING MULTICAST DUE TO SEND ERROR");
                this.stop = true;
            } catch (InterruptedException e) {
                System.out.println("CLOSING MULTICAST DUE TO SLEEP ERROR");
                this.stop = true;
            }
        }
        System.out.println("CLOSING MULTICAST");
        socket.close();
    }

    public void cancel() {
        this.stop = true;
    }
}
