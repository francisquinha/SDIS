import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

class Multicast extends Thread {

    private static final boolean LOG = false;
    private static final int PACKET_SIZE = 65507;

    private final InetAddress address;
    private MulticastSocket socket;
    private DatagramPacket sendPacket;
    private DatagramPacket receivePacket;
    private boolean stop;

    Multicast(InetAddress address, int port) {
        this.address = address;
        try {
            socket = new MulticastSocket(port);
            socket.setLoopbackMode(false);                                                                              // receive my own packets, in order to deal with my putchunk packets correctly
            socket.joinGroup(this.address);
        } catch (Exception e) {
            System.out.println("Error opening multicast socket or joining multicast group");
            return;
        }
        byte[] data = new byte[PACKET_SIZE];
        sendPacket = new DatagramPacket(data, data.length, this.address, port);
        receivePacket = new DatagramPacket(data, data.length);
        stop = false;
    }

    synchronized void send(Message message) {
        byte[] messageBytes = message.getBytes();
        sendPacket.setData(messageBytes);
        sendPacket.setLength(messageBytes.length);
        try {
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            if (!stop) System.out.println("Error sending multicast packet");
            return;
        }
        if (LOG) System.out.println("OUT: " + message.getHeader());
    }

    private MessageType getMessageType(DatagramPacket packet) {
        String messageString = new String(packet.getData(), packet.getOffset(), packet.getLength());
        int index = messageString.indexOf(" ");
        if (index == -1) return MessageType.OTHER;
        MessageType messageType;
        try {
            messageType = MessageType.valueOf(messageString.substring(0, index));
        }
        catch (IllegalArgumentException e) {
            return MessageType.OTHER;
        }
        return messageType;
    }

    public void run() {
        while (!stop) {
            try {
                socket.receive(receivePacket);
            } catch (IOException e) {
                if (!stop) System.out.println("Error receiving multicast packet");
                continue;
            }
            if (LOG) {
                Message message = new Message(receivePacket);
                System.out.println("IN: " + message.getHeader());
            }
            MessageType messageType = getMessageType(receivePacket);
            switch (messageType) {
                case PUTCHUNK:
                    PutchunkMessage putchunkMessage = new PutchunkMessage(receivePacket);
                    putchunkMessage.start();
                    break;
                case STORED:
                    StoredMessage storedMessage = new StoredMessage(receivePacket);
                    storedMessage.start();
                    break;
                case GETCHUNK:
                    GetchunkMessage getchunkMessage = new GetchunkMessage(receivePacket);
                    getchunkMessage.start();
                    break;
                case CHUNK:
                    ChunkMessage chunkMessage = new ChunkMessage(receivePacket);
                    chunkMessage.start();
                    break;
                case DELETE:
                    DeleteMessage deleteMessage = new DeleteMessage(receivePacket);
                    deleteMessage.start();
                    break;
                case REMOVED:
                    RemovedMessage removedMessage = new RemovedMessage(receivePacket);
                    removedMessage.start();
                    break;
            }
        }
    }

    void close() {
        stop = true;
        System.out.println("Closing multicast socket");
        try {
            socket.leaveGroup(address);
        } catch (IOException e) {
            System.out.println("Error leaving multicast group");
        }
        socket.close();
    }

}
