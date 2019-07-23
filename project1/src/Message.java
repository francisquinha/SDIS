import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Pattern;

class Message extends Thread {

    private static final String CRLF = Character.toString((char)13) + Character.toString((char)10); // 0xD 0xA

    private String header;
    private byte[] messageBytes;
    private MessageType messageType;
    private int[] version;
    private int senderId;
    private String fileId;
    private int chunkNo;
    private int replicationDeg;
    private byte[] body;

    Message(DatagramPacket packet) {
        messageBytes = packet.getData();
        String messageString = new String(messageBytes, packet.getOffset(), packet.getLength());
        int index = messageString.indexOf(CRLF);
        if (index == -1) {
            messageType = MessageType.OTHER;
            return;
        }
        header = messageString.substring(0, index);
        String[] headerParts = header.split(" +");
        try {
            messageType = MessageType.valueOf(headerParts[0]);
            if (!Pattern.matches("\\d\\.\\d", headerParts[1])) {
                messageType = MessageType.OTHER;
                return;
            }
            String[] versionParts = headerParts[1].split("\\.");
            version = new int[]{Integer.parseInt(versionParts[0]), Integer.parseInt(versionParts[1])};
            senderId = Integer.parseInt(headerParts[2]);
            fileId = headerParts[3];
            if (messageType != MessageType.DELETE) {
                chunkNo = Integer.parseInt(headerParts[4]);
            }
            if (messageType == MessageType.PUTCHUNK) {
                replicationDeg = Integer.parseInt(headerParts[5]);
            }
        }
        catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            messageType = MessageType.OTHER;
            return;
        }
        if (messageType == MessageType.PUTCHUNK || messageType == MessageType.CHUNK) {
            if (messageString.length() > index + 4)
                body = messageBytes = Arrays.copyOfRange(messageBytes, index + 4, packet.getLength());
            else body = new byte[0];
        }
    }

    Message(MessageType messageType, int[] version,
            int senderId, String fileId, int chunkNo, int replicationDeg, byte[] body) {
        this.messageType = messageType;
        this.version = version;
        this.senderId = senderId;
        this.fileId = fileId;
        this.chunkNo = chunkNo;
        this.replicationDeg = replicationDeg;
        this.body = body;
        header = messageType + " " + version[0] + "." + version[1] + " "
                + senderId + " " + fileId;
        if (messageType != MessageType.DELETE) {
            header += " " + chunkNo;
        }
        if (messageType == MessageType.PUTCHUNK) {
            header += " " + replicationDeg;
        }
        byte[] headerBytes = (header + CRLF + CRLF).getBytes();
        if (messageType == MessageType.PUTCHUNK || messageType == MessageType.CHUNK)
            messageBytes = ByteBuffer.allocate(headerBytes.length + body.length).put(headerBytes).put(body).array();
        else messageBytes = headerBytes;
    }

    String getHeader() {
        return header;
    }

    byte[] getBytes() {
        return messageBytes;
    }

    int[] getVersion() {
        return version;
    }

    int getSenderId() {
        return senderId;
    }

    String getFileId() {
        return fileId;
    }

    int getChunkNo() {
        return chunkNo;
    }

    int getReplicationDeg() {
        return replicationDeg;
    }

    byte[] getBody() {
        return body;
    }

}
