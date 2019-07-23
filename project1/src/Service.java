import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

class Service extends Thread {

    private static final boolean LOG = true;
    private static final int CHUNK_SIZE = 64000;
    private static final long MAX_FILE_SIZE = 64000000000L;
    private static final int PACKET_SIZE = 65507;
    private static final int MAX_PUTCHUNK_ATTEMPTS = 5;
    private static final int MAX_GETCHUNK_ATTEMPTS = 5;
    private static final int MAX_DELETE_ATTEMPTS = 5;
    private static final int SLEEP_TIME = 1000;
    private static final int SMALL_SLEEP_TIME = 100;

    private static String serviceIP;
    private static DatagramSocket serviceSocket;
    private static DatagramPacket servicePacket;
    private static boolean stop;

    Service(int serverId) {
        stop = false;
        try {
            serviceSocket = new DatagramSocket(serverId);
        }
        catch (SocketException e) {
            System.out.println("Error opening socket for TestApp");
        }
        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress(InetAddress.getByName("google.pt"), 80));
            serviceIP = sock.getLocalAddress().getHostAddress();
            sock.close();
        } catch (IOException e) {
            System.out.println("Error getting local IP address for TestApp");
        }
        byte[] data = new byte[PACKET_SIZE];
        servicePacket = new DatagramPacket(data, data.length);
    }

    String getServiceIP() {
        return serviceIP;
    }

    private String getHash(String text) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        md.update(text.getBytes());
        byte[] digest = md.digest();
        StringBuilder builder = new StringBuilder();
        for (byte digestByte : digest)
            builder.append(String.format("%02x", digestByte));
        return builder.toString();
    }

    private void reply(String result, InetAddress clientAddress, int clientPort) {
        byte[] sendData = result.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
        try {
            serviceSocket.send(sendPacket);
        } catch (IOException e) {
            if (!stop) System.out.println("Error sending service packet");
        }
    }

    private void backupFile(String filePath, int replicationDeg, InetAddress clientAddress, int clientPort) {
        Peer peer = Peer.getInstance();
        String fileId;
        File file = new File(filePath);
        long fileSize = file.length();
        if (fileSize > MAX_FILE_SIZE) {
            reply("File too large for backup", clientAddress, clientPort);
            return;
        }
        FileInputStream in;
        try {
            in = new FileInputStream(file);
            fileId = getHash(filePath + file.lastModified());
            if (fileId == null) {
                reply("Error computing fileId", clientAddress, clientPort);
                return;
            }
            FileMetadata fileMetadata = new FileMetadata(filePath);
            peer.addFileMetadata(fileId, fileMetadata);
            int chunkNo = 0;
            int chunkSize;
            byte[] chunkBytes = new byte[CHUNK_SIZE];
            do {
                chunkSize = in.read(chunkBytes);
                fileMetadata.addChunk(fileId, chunkNo, chunkSize, replicationDeg);
                if (chunkSize == -1) chunkBytes = new byte[0];
                else chunkBytes = Arrays.copyOfRange(chunkBytes, 0, chunkSize);
                Putchunk putchunk = new Putchunk(peer.getVersion(), peer.getServerId(),
                        fileId, chunkNo, replicationDeg, chunkBytes);
                putchunk.start();
                try {
                    sleep(SMALL_SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                chunkNo++;
            }
            while (chunkSize == CHUNK_SIZE);
            in.close();
        } catch (IOException e) {
            reply("Cannot open file", clientAddress, clientPort);
            return;
        }
        int attempts = 0;
        int sleepTime = SLEEP_TIME;
        FileMetadata fileMetadata = peer.getFileMetadata(fileId);
        while (attempts < MAX_PUTCHUNK_ATTEMPTS) {
            if (fileMetadata.getBackupDone()) break;
            try {
                sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sleepTime *= 2;
            attempts++;
        }
        reply(fileMetadata.getRepDegrees(), clientAddress, clientPort);
        if (LOG) System.out.println("File '" + filePath + "' backed up, mininum chunk replication degree "
                + fileMetadata.getMinChunkRepDeg() + ".");
        fileMetadata.setChanged(true);
    }

    private void restoreFile(String filePath, InetAddress clientAddress, int clientPort) {
        Peer peer = Peer.getInstance();
        String fileId = peer.getFileId(filePath);
        if (fileId == null) {
            reply("Cannot restore file, nonexistent backup", clientAddress, clientPort);
            return;
        }
        FileMetadata fileMetadata = peer.getFileMetadata(fileId);
        fileMetadata.setRestore(true);
        int numberChunks = fileMetadata.getNumberChunks();
        for (int chunkNo = 0; chunkNo < numberChunks; chunkNo++) {
            Message message = new Message(MessageType.GETCHUNK, peer.getVersion(),
                    peer.getServerId(), fileId, chunkNo, 0, null);
            peer.getMulticastControl().send(message);
            try {
                sleep(SMALL_SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        int attempts = 0;
        int sleepTime = SLEEP_TIME;
        while (attempts < MAX_GETCHUNK_ATTEMPTS) {
            if (fileMetadata.getRestoreDone()) {
                try {
                    sleep(SMALL_SLEEP_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int index = filePath.contains(".") ? filePath.lastIndexOf('.') : filePath.length();
                FileOutputStream out;
                try {
                    out = new FileOutputStream(filePath.substring(0, index) + "_restore" + filePath.substring(index));
                    for (int chunkNo = 0; chunkNo < numberChunks; chunkNo++) {
                        byte[] chunk = peer.removeRestoreChunk(fileId + chunkNo);
                        out.write(chunk);
                    }
                    out.close();
                } catch (IOException e) {
                    reply("Cannot restore file, error writing", clientAddress, clientPort);
                    fileMetadata.setRestore(false);
                    return;
                }
                reply("File restored", clientAddress, clientPort);
                if (LOG) System.out.println("File '" + filePath + "' restored.");
                fileMetadata.setRestore(false);
                return;
            }
            try {
                sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sleepTime *= 2;
            attempts++;
        }
        reply("Cannot restore file, error recovering chunks", clientAddress, clientPort);
        fileMetadata.setRestore(false);
    }

    private void deleteFile(String filePath, InetAddress clientAddress, int clientPort) {
        Peer peer = Peer.getInstance();
        String fileId = peer.getFileId(filePath);
        if (fileId == null) {
            reply("Cannot delete file, nonexistent backup", clientAddress, clientPort);
            return;
        }
        Message message = new Message(MessageType.DELETE, peer.getVersion(), peer.getServerId(), fileId, 0, 0, null);
        int attempts = 0;
        while (attempts < MAX_DELETE_ATTEMPTS) {
            peer.getMulticastControl().send(message);
            try {
                sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attempts++;
        }
        peer.removeFileMetadata(fileId);
        reply("File deleted from backup", clientAddress, clientPort);
        if (LOG) System.out.println("File '" + filePath + "' deleted from backup.");
    }

    private void reclaimSpace(long space, InetAddress clientAddress, int clientPort) {
        Peer peer = Peer.getInstance();
        if (space > peer.getInitialSpace()) space = peer.getInitialSpace();
        if (peer.getCurrentSpace() >= space) {
            peer.setCurrentSpace(peer.getCurrentSpace() - space);
            peer.setInitialSpace(peer.getInitialSpace() - space);
            reply("Space reclaimed without deleting chunks, current total space of backup is " +
                    peer.getInitialSpace(), clientAddress, clientPort);
            if (LOG) printSpaceLog(peer, space);
            return;
        }
        long new_space = space - peer.getCurrentSpace();
        int recoveredSpace = peer.removeChunks(new_space, true);
        if (recoveredSpace >= new_space) {
            peer.setCurrentSpace(peer.getCurrentSpace() - space);
            peer.setInitialSpace(peer.getInitialSpace() - space);
            reply("Space reclaimed deleting safe chunks, current total space of backup is " +
                    peer.getInitialSpace(), clientAddress, clientPort);
            if (LOG) printSpaceLog(peer, space);
            return;
        }
        new_space -= recoveredSpace;
        peer.removeChunks(new_space, false);
        peer.setCurrentSpace(peer.getCurrentSpace() - space);
        peer.setInitialSpace(peer.getInitialSpace() - space);
        reply("Space reclaimed deleting safe and unsafe chunks, current total space of backup is " +
                peer.getInitialSpace(), clientAddress, clientPort);
        if (LOG) printSpaceLog(peer, space);
    }

    private void printSpaceLog(Peer peer, long space) {
        System.out.println("Reclaimed " + space/1000 + "kB of space, current total space is "
                + peer.getInitialSpace()/1000 + "kB, available space is "
                + peer.getCurrentSpace()/1000 + "kB." );
    }

    private void processRequest(DatagramPacket receivePacket) {
        InetAddress clientAddress = receivePacket.getAddress();
        int clientPort = receivePacket.getPort();
        String messageString = new String(receivePacket.getData(),
                receivePacket.getOffset(), receivePacket.getLength());
        ServiceMessageType messageType;
        String[] messageParts = messageString.split(" +");
        try {
            messageType = ServiceMessageType.valueOf(messageParts[0]);
        }
        catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            Peer peer = Peer.getInstance();
            if ((Arrays.equals(peer.getVersion(), new int[]{1, 2})
                    || Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                               // if this is version 1.2 - enhancement 2 or 2.0 - all enhancements
                    && processRequest_v_1_2(receivePacket, messageParts[0])) return;                                    // check for enhancement 2 chunk message
            reply("Invalid message format", clientAddress, clientPort);
            return;
        }
        String filePath;
        int replicationDeg;
        long space;
        switch (messageType) {
            case BACKUP:
                try {
                    filePath = messageParts[1];
                    replicationDeg = Integer.parseInt(messageParts[2]);
                }
                catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    reply("Invalid message format", clientAddress, clientPort);
                    return;
                }
                Thread backup = new Thread(() -> {backupFile(filePath, replicationDeg, clientAddress, clientPort);});
                backup.start();
                break;
            case RESTORE:
                try {
                    filePath = messageParts[1];
                }
                catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    reply("Invalid message format", clientAddress, clientPort);
                    return;
                }
                Thread restore = new Thread(() -> {restoreFile(filePath, clientAddress, clientPort);});
                restore.start();
                break;
            case DELETE:
                try {
                    filePath = messageParts[1];
                }
                catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    reply("Invalid message format", clientAddress, clientPort);
                    return;
                }
                Thread delete = new Thread(() -> {deleteFile(filePath, clientAddress, clientPort);});
                delete.start();
                break;
            case RECLAIM:
                try {
                    space = Long.parseLong(messageParts[1]);
                }
                catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                    reply("Invalid message format", clientAddress, clientPort);
                    return;
                }
                Thread reclaim = new Thread(() -> {reclaimSpace(space, clientAddress, clientPort);});
                reclaim.start();
                break;
        }
    }

    private boolean processRequest_v_1_2(DatagramPacket receivePacket, String messageParts0) {
        try {
            MessageType messageType1 = MessageType.valueOf(messageParts0);
            if (messageType1 == MessageType.CHUNK) {                                                                    // check if this is a chunk message from a peer
                ChunkMessage chunkMessage = new ChunkMessage(receivePacket);                                            // initiate chunk message protocol
                chunkMessage.start();
                return true;
            }
            return false;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void run() {
        while (!stop) {
            try {
                serviceSocket.receive(servicePacket);
            } catch (IOException e) {
                if (!stop) System.out.println("Error receiving service packet");
                continue;
            }
            processRequest(servicePacket);
        }
    }

    void close() {
        stop = true;
        System.out.println("Stopping Service...");
        serviceSocket.close();
    }

}
