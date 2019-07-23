import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

class Peer extends Thread {

    private static final boolean LOG = true;
    private static final int PORT_INI = 1;                                                                              // safer with 49152
    private static final int PORT_FIN = 65535;
    private static final String IP_INI = "224.0.0.0";                                                                   // safer with "239.0.0.0"
    private static final String IP_FIN = "239.255.255.255";
    private static final String IP_PATTERN = "(22[4-9]|23[0-9])(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}";              // safer with "239(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}"
    private static final long MAX_NUMBER_CHUNKS = 1000000;
    private static final int SLEEP_TIME = 1000;                                                                         // only used in version 1.3 - enhancement 3
    private static final int MAX_INITIAL_ATTEMPTS = 2;                                                                  // only used in version 1.3 - enhancement 3
    private static final int TIME_BETWEEN_SAVES = 10000;

    private static Peer instance;

    private static int serverId;
    private static int[] version;
    private static long initialSpace;
    private static long currentSpace;
    private static ConcurrentHashMap<String, FileMetadata> fileMetadatas;
    private static ConcurrentHashMap<String, ChunkMetadata> chunkMetadatas;
    private static ConcurrentHashMap<String, byte[]> restoreChunks;
    private static Multicast multicastControl;
    private static Multicast multicastBackup;
    private static Multicast multicastRestore;
    private static Service service;
    private static Random random;
    private static boolean stop;

    public static void main(String[] args) {
        parseArgs(args);
        if (instance == null) return;
        if (!createDirectory("chunks" + serverId)) return;
        if (!createDirectory("metadatas")) return;
        if (!createDirectory("metadatas/fileMetadatas")) return;
        if (!createDirectory("metadatas/chunkMetadatas")) return;
        if (!createDirectory("metadatas/fileMetadatas/" + serverId)) return;
        if (!createDirectory("metadatas/chunkMetadatas/" + serverId)) return;
        instance.start();
        BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
        try {
            buffer.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        instance.close();
    }

    private static void parseArgs(String[] args) {
        if (args.length != 9 || !Pattern.matches("\\d\\.\\d", args[1])) {
            howTo();
            instance = null;
            return;
        }
        int serverId, version0, version1, controlPort, backupPort, restorePort;
        long initialSpace;
        InetAddress controlAddress, backupAddress, restoreAddress;
        try {
            serverId = Integer.parseInt(args[0]);
            String[] versionParts = args[1].split("\\.");
            version0 = Integer.parseInt(versionParts[0]);
            version1 = Integer.parseInt(versionParts[1]);
            initialSpace = Long.parseLong(args[2]) * 1000;
            controlPort = Integer.parseInt(args[4]);
            backupPort = Integer.parseInt(args[6]);
            restorePort = Integer.parseInt(args[8]);
        } catch (NumberFormatException e) {
            howTo();
            instance = null;
            return;
        }
        if (serverId < 0 || controlPort < PORT_INI || backupPort < PORT_INI || restorePort < PORT_INI
                || controlPort > PORT_FIN || backupPort > PORT_FIN || restorePort > PORT_FIN
                || controlPort == backupPort || controlPort == restorePort || backupPort == restorePort
                || initialSpace < 0 || !acceptedVersion(version0, version1)) {
            howTo();
            instance = null;
            return;
        }
        if (Objects.equals(args[3], args[5]) || Objects.equals(args[3], args[7]) || Objects.equals(args[5], args[7]) ||
                !Pattern.matches(IP_PATTERN, args[3]) ||
                !Pattern.matches(IP_PATTERN, args[5]) ||
                !Pattern.matches(IP_PATTERN, args[7])) {
            howTo();
            instance = null;
            return;
        }
        try {
            controlAddress = InetAddress.getByName(args[3]);
            backupAddress = InetAddress.getByName(args[5]);
            restoreAddress = InetAddress.getByName(args[7]);
        } catch (UnknownHostException e) {
            howTo();
            instance = null;
            return;
        }
        instance = new Peer(serverId, version0, version1, initialSpace, controlAddress, controlPort,
                backupAddress, backupPort, restoreAddress, restorePort);
        instance.checkChunks();
        info(service.getServiceIP(), args[3], controlPort, args[5], backupPort, args[7], restorePort);
    }

    private static boolean acceptedVersion(int version0, int version1) {
        return (version0 == 1 && version1 == 0)
                || (version0 == 1 && version1 == 1)
                || (version0 == 1 && version1 == 2)
                || (version0 == 1 && version1 == 3)
                || (version0 == 1 && version1 == 4)
                || (version0 == 2 && version1 == 0);
    }

    private static void howTo() {
        System.out.println("Peer must be invoked as follows: java Peer <serverId> <version> " +
                "<diskSpace> <controlIP> <controlPort> <backupIP> <backupPort> <restoreIP> <restorePort>:");
        System.out.println("serverId must be a positive integer at most " + PORT_FIN + ";");
        System.out.println("diskSpace is the available space for backup in thousands of bytes " +
                "and must be a non negative long;");
        System.out.println("version must be 1.0, 1.1, 1.2, 1.3, 1.4 or 2.0" + ";");
        System.out.println("controlPort, backupPort and restorePort must be distinct integer " +
                "and valid port numbers in the range " + PORT_INI + " - " + PORT_FIN + ";");
        System.out.println("controlIP, backupIP and restoreIP must be distinct valid multicast " +
                "intranet IP addresses in the range " + IP_INI + "-" + IP_FIN + ".");
    }

    private static void info(String serviceIP, String controlIP, int controlPort,
                             String backupIP, int backupPort, String restoreIP, int restorePort) {
        System.out.println("Backup Service at IP Address " + serviceIP + " and Port " + serverId + ".");
        System.out.println("Multicast Control at IP Address " + controlIP + " and Port " + controlPort + ".");
        System.out.println("Multicast Data Backup at IP Address " + backupIP + " and Port " + backupPort + ".");
        System.out.println("Multicast Data Restore at IP Address " + restoreIP + " and Port " + restorePort + ".");
        System.out.println("Use Enter to stop Peer...");
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean createDirectory(String directoryName) {
        File directory = new File(directoryName);
        if (!directory.exists()) {
            try {
                directory.mkdir();
            }
            catch (SecurityException e) {
                System.out.println("Unable to create " + directoryName + " directory");
                return false;
            }
        }
        return true;
    }

    private Peer(int serverId, int version0, int version1, long space, InetAddress controlAddress, int controlPort,
                 InetAddress backupAddress, int backupPort, InetAddress restoreAddress, int restorePort) {
        Peer.serverId = serverId;
        version = new int[]{version0, version1};
        initialSpace = space;
        currentSpace = space;
        if (!restoreFileMetadatas()) fileMetadatas = new ConcurrentHashMap<>();
        if (!restoreChunkMetadatas()) chunkMetadatas = new ConcurrentHashMap<>();
        restoreChunks = new ConcurrentHashMap<>();
        multicastControl = new Multicast(controlAddress, controlPort);
        multicastBackup = new Multicast(backupAddress, backupPort);
        multicastRestore = new Multicast(restoreAddress, restorePort);
        service = new Service(serverId);
        random = new Random();
        stop = false;
    }

    private void checkChunks_v_1_3() {
        for (ChunkMetadata chunkMetadata : chunkMetadatas.values()) {
            Thread initialGetchunk = new Thread(() -> {initialGetchunk(chunkMetadata);});
            initialGetchunk.start();
        }
        try {
            Thread.sleep(SLEEP_TIME * (MAX_INITIAL_ATTEMPTS + 1));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //noinspection Convert2streamapi
        for (ChunkMetadata chunkMetadata : chunkMetadatas.values()) {
            if (chunkMetadata.getRepDeg() > 1 || chunkMetadata.getNotDeleted()) {                                       // if the replication degree is 1, there is no point in determining if other peers still have the chunk, unless someone replied to our previous getchunk
                chunkMetadata.resetPeerIds();
                byte[] chunk = getChunk(chunkMetadata.getFileId() + chunkMetadata.getNumber());
                Putchunk putchunk = new Putchunk(version, serverId, chunkMetadata.getFileId(),                          // use putchunk protocol to determine the current replication degree of my chunks
                        chunkMetadata.getNumber(), chunkMetadata.getMinRepDeg(), chunk);
                putchunk.start();
            }
        }
    }

    private void initialGetchunk(ChunkMetadata chunkMetadata) {
        Message message = new Message(MessageType.GETCHUNK, version, serverId,                                          // use getchunk messages to determine if chunk has been deleted
                chunkMetadata.getFileId(), chunkMetadata.getNumber(), 0, null);
        int attempts = 0;
        while (attempts < MAX_INITIAL_ATTEMPTS) {
            multicastControl.send(message);
            try {
                Thread.sleep(SLEEP_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (chunkMetadata.getNotDeleted()) return;
            attempts++;
        }
        if (chunkMetadata.getRepDeg() > 1)                                                                              // if the replication degree is 1, do not delete even if no one replied
            deleteChunk(chunkMetadata.getFileId() + chunkMetadata.getNumber());
    }

    static Peer getInstance() {
        return instance;
    }

    long getInitialSpace() {
        return initialSpace;
    }

    long getCurrentSpace() {
        return currentSpace;
    }

    Random getRandom() {
        return random;
    }

    int getServerId() {
        return serverId;
    }

    int[] getVersion() {
        return version;
    }

    Multicast getMulticastControl() {
        return multicastControl;
    }

    Multicast getMulticastBackup() {
        return multicastBackup;
    }

    Multicast getMulticastRestore() {
        return multicastRestore;
    }

    long getSpace() {
        return currentSpace;
    }

    void setCurrentSpace(long currentSpace) {
        Peer.currentSpace = currentSpace;
    }

    void setInitialSpace(long initialSpace) {
        Peer.initialSpace = initialSpace;
    }

    boolean storedChunk(String chunkId) {
        return (chunkMetadatas.get(chunkId) != null);
    }

    boolean reallyStoredChunk(String chunkId) {
        return (chunkMetadatas.get(chunkId) != null && chunkMetadatas.get(chunkId).getStored());
    }

    ChunkMetadata getChunkMetadata(String chunkId) {
        return chunkMetadatas.get(chunkId);
    }

    private void saveFileMetadatas() {
        FileMetadata fileMetadata;
        for (Map.Entry<String, FileMetadata> entry : fileMetadatas.entrySet()) {
            fileMetadata = entry.getValue();
            if (!fileMetadata.getChanged()) continue;
            try {
                FileOutputStream fileOut = new FileOutputStream("metadatas/fileMetadatas/" + serverId + "/"
                        + entry.getKey() + ".ser");
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(fileMetadata);
                out.close();
                fileOut.close();
                fileMetadata.setChanged(false);
            } catch (IOException e) {
                System.out.println("Error saving file metadata to file");
                return;
            }
        }
    }

    private void saveChunkMetadatas() {
        ChunkMetadata chunkMetadata;
        for (Map.Entry<String, ChunkMetadata> entry : chunkMetadatas.entrySet()) {
            chunkMetadata = entry.getValue();
            if (!chunkMetadata.getChanged()) continue;
            try {
                FileOutputStream fileOut = new FileOutputStream("metadatas/chunkMetadatas/" + serverId + "/"
                        + entry.getKey() + ".ser");
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                out.writeObject(chunkMetadata);
                out.close();
                fileOut.close();
                chunkMetadata.setChanged(false);
            } catch (IOException e) {
                System.out.println("Error saving file metadata to file");
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean restoreFileMetadatas() {
        File directory = new File("metadatas/fileMetadatas/" + serverId);
        File[] listOfFiles = directory.listFiles();
        if (listOfFiles == null) return false;
        fileMetadatas = new ConcurrentHashMap<>();
        for (File file : listOfFiles) {
            String fileId;
            if ((fileId = checkFile(file)) == null) continue;
            try {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                fileMetadatas.put(fileId, (FileMetadata)in.readObject());
                in.close();
                fileIn.close();
            } catch (IOException | ClassNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean restoreChunkMetadatas() {
        File directory = new File("metadatas/chunkMetadatas/" + serverId);
        File[] listOfFiles = directory.listFiles();
        if (listOfFiles == null) return false;
        chunkMetadatas = new ConcurrentHashMap<>();
        for (File file : listOfFiles) {
            String chunkId;
            if ((chunkId = checkFile(file)) == null) continue;
            try {
                FileInputStream fileIn = new FileInputStream(file);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                chunkMetadatas.put(chunkId, (ChunkMetadata) in.readObject());
                in.close();
                fileIn.close();
            } catch (IOException | ClassNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    private void removeChunkMetadataSer(String chunkId) {
        new File("metadatas/chunkMetadatas/" + serverId + "/" + chunkId + ".ser").delete();
    }

    private void removeFileMetadataSer(String fileId) {
        new File("metadatas/fileMetadatas/" + serverId + "/" + fileId + ".ser").delete();
    }

    private String checkFile(File file) {
        if (!file.isFile()) return null;
        String fileName = file.getName();
        int index = fileName.lastIndexOf(".");
        if (index == -1) return null;
        if (Objects.equals(fileName.substring(index, fileName.length()), ".ser"))
            return fileName.substring(0, index);
        return null;
    }

    private void checkChunks() {
        Map.Entry<String, ChunkMetadata> entry;
        ChunkMetadata chunkMetadata;
        for (Iterator<Map.Entry<String, ChunkMetadata>> it = chunkMetadatas.entrySet().iterator(); it.hasNext(); ) {
            entry = it.next();
            chunkMetadata = entry.getValue();
            if (getChunk(chunkMetadata.getFileId() + chunkMetadata.getNumber()) == null) {
                Message message = new Message(MessageType.REMOVED, version, serverId, chunkMetadata.getFileId(),
                        chunkMetadata.getNumber(), 0, null);
                multicastControl.send(message);
                new File(chunkMetadata.getPath()).delete();
                removeChunkMetadataSer(chunkMetadata.getFileId() + chunkMetadata.getNumber());
                it.remove();
            }
            else currentSpace -= chunkMetadata.getSize();
        }
    }

    boolean addChunk(String fileId, int chunkNo, byte[] body, int chunkSize, int replicationDeg) {
        ChunkMetadata chunkMetadata = new ChunkMetadata(fileId, chunkNo, chunkSize, replicationDeg);
        if (!storeChunk(chunkMetadata, body)) return false;
        chunkMetadatas.put(fileId + chunkNo, chunkMetadata);
        return true;
    }

    ChunkMetadata addChunkMetadata(String fileId, int chunkNo, int chunkSize, int replicationDeg) {
        ChunkMetadata chunkMetadata = new ChunkMetadata(fileId, chunkNo, chunkSize, replicationDeg);
        chunkMetadatas.put(fileId + chunkNo, chunkMetadata);
        return chunkMetadata;
    }

    void removeChunkMetadata(String fileId, int chunkNo) {
        chunkMetadatas.remove(fileId + chunkNo);
        removeChunkMetadataSer(fileId + chunkNo);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean storeChunk(ChunkMetadata chunkMetadata, byte[] body) {
        if (currentSpace < chunkMetadata.getSize()) return false;
        try (FileOutputStream out = new FileOutputStream(chunkMetadata.getPath())) {
            out.write(body);
            out.close();
        } catch (IOException e) {
            new File(chunkMetadata.getPath()).delete();
            return false;
        }
        currentSpace -= chunkMetadata.getSize();
        chunkMetadata.setStored();
        if (LOG) System.out.println("Stored chunk "
                + chunkMetadata.getFileId().substring(60, 64) + "." + chunkMetadata.getNumber()
                + ", space remaining is " + currentSpace/1000 + "kB, total space is " + initialSpace/1000 + "kB.");
        return true;
    }

    byte[] getChunk(String chunkId) {
        ChunkMetadata chunkMetadata = chunkMetadatas.get(chunkId);
        byte[] chunk = new byte[chunkMetadata.getSize()];
        try (FileInputStream in = new FileInputStream((chunkMetadata.getPath()))) {
            if (in.read(chunk) != chunkMetadata.getSize()) return null;
        } catch (IOException e) {
            return null;
        }
        return chunk;
    }

    private void deleteChunk(String chunkId) {
        ChunkMetadata chunkMetadata = chunkMetadatas.get(chunkId);
        if (chunkMetadata == null) return;
        new File(chunkMetadata.getPath()).delete();
        currentSpace += chunkMetadata.getSize();
        chunkMetadatas.remove(chunkId);
        removeChunkMetadataSer(chunkId);
        if (LOG) System.out.println("Removed chunk " + chunkMetadata.getFileId().substring(60, 64) + "."
                + chunkMetadata.getNumber() + ", space remaining is " + currentSpace/1000
                + "kB, total space is " + initialSpace/1000 + "kB.");
    }

    void removeNullChunk(String fileId, int chunkNo) {
        deleteChunk(fileId + chunkNo);
        Message reply = new Message(MessageType.REMOVED, version, serverId, fileId, chunkNo, 0, null);
        multicastControl.send(reply);
    }

    void deleteFileChunks(String fileId) {
        for (int i = 0; i < MAX_NUMBER_CHUNKS; i++)
            deleteChunk(fileId + i);
    }

    boolean myBackupFile(String fileId) {
        return (fileMetadatas.get(fileId) != null);
    }

    boolean myRestoreFile(String fileId) {
        FileMetadata fileMetadata = fileMetadatas.get(fileId);
        return fileMetadata != null && fileMetadata.getRestore();
    }

    FileMetadata getFileMetadata(String fileId) {
        return fileMetadatas.get(fileId);
    }

    void addFileMetadata(String fileId, FileMetadata fileMetadata) {
        fileMetadatas.put(fileId, fileMetadata);
    }

    void removeFileMetadata(String fileId) {
        fileMetadatas.remove(fileId);
        removeFileMetadataSer(fileId);
    }

    boolean checkRepDeg(String fileId, int chunkNo) {
        if (myBackupFile(fileId)) {
            FileMetadata fileMetadata = fileMetadatas.get(fileId);
            return fileMetadata.getRepDeg(chunkNo) >= fileMetadata.getMinRepDeg(chunkNo);
        }
        if (storedChunk(fileId + chunkNo)) {
            ChunkMetadata chunkMetadata = chunkMetadatas.get(fileId + chunkNo);
            return chunkMetadata.getRepDeg() >= chunkMetadata.getMinRepDeg();
        }
        return false;
    }

    void setChunkBackupDone(String fileId, int chunkNo) {
        FileMetadata fileMetadata = fileMetadatas.get(fileId);
        fileMetadata.setChunkBackupDone(chunkNo);
        fileMetadata.setBackupDone();
    }

    void addRestoreChunks(String fileId, int chunkNo, byte[] body) {
        byte[] chunk = restoreChunks.get(fileId + chunkNo);
        if (chunk == null || body.length > chunk.length) restoreChunks.put(fileId + chunkNo, body);
    }

    byte[] removeRestoreChunk(String chunkId) {
        return restoreChunks.remove(chunkId);
    }

    String getFileId(String filePath) {
        for (Map.Entry<String, FileMetadata> entry : fileMetadatas.entrySet()) {
            if (Objects.equals(entry.getValue().getPath(), filePath))
                return entry.getKey();
        }
        return null;
    }

    void setChunkRestoreDone(String fileId, int chunkNo) {
        FileMetadata fileMetadata = fileMetadatas.get(fileId);
        fileMetadata.setChunkRestoreDone(chunkNo);
        fileMetadata.setRestoreDone();
    }

    int removeChunks(long space, boolean safe) {
        Map.Entry<String, ChunkMetadata> entry;
        ChunkMetadata chunkMetadata;
        int recoveredSpace = 0;
        for (Iterator<Map.Entry<String, ChunkMetadata>> it = chunkMetadatas.entrySet().iterator(); it.hasNext(); ) {
            if (recoveredSpace >= space) break;
            entry = it.next();
            chunkMetadata = entry.getValue();
            if ((!safe && chunkMetadata.getRepDeg() > 1) || chunkMetadata.getRepDeg() > chunkMetadata.getMinRepDeg()) {
                Message message = new Message(MessageType.REMOVED, version, serverId, chunkMetadata.getFileId(),
                        chunkMetadata.getNumber(), 0, null);
                multicastControl.send(message);
                new File(chunkMetadata.getPath()).delete();
                removeChunkMetadataSer(chunkMetadata.getFileId() + chunkMetadata.getNumber());
                it.remove();
                recoveredSpace += chunkMetadata.getSize();
                currentSpace += chunkMetadata.getSize();
                if (LOG) System.out.println("Removed chunk " + chunkMetadata.getFileId().substring(60, 64) + "."
                        + chunkMetadata.getNumber() + ", space remaining is " + currentSpace/1000
                        + "kB, total space is " + initialSpace/1000 + "kB.");
            }
        }
        if (safe || recoveredSpace >= space) return recoveredSpace;
        for (Iterator<Map.Entry<String, ChunkMetadata>> it = chunkMetadatas.entrySet().iterator(); it.hasNext(); ) {    // if there are chunks left, they have replication degree 1
            entry = it.next();
            chunkMetadata = entry.getValue();
            byte[] chunk = getChunk(chunkMetadata.getFileId() + chunkMetadata.getNumber());
            Putchunk putchunk = new Putchunk(version, serverId, chunkMetadata.getFileId(),                              // before deleting chunk of replication degree 1, start putchunk protocol for that chunk
                    chunkMetadata.getNumber(), chunkMetadata.getMinRepDeg(), chunk);
            putchunk.start();
            Message message = new Message(MessageType.REMOVED, version, serverId, chunkMetadata.getFileId(),
                    chunkMetadata.getNumber(), 0, null);
            multicastControl.send(message);
            new File(chunkMetadata.getPath()).delete();
            removeChunkMetadataSer(chunkMetadata.getFileId() + chunkMetadata.getNumber());
            it.remove();
            recoveredSpace += chunkMetadata.getSize();
            currentSpace += chunkMetadata.getSize();
            if (LOG) System.out.println("Removed chunk " + chunkMetadata.getFileId().substring(60, 64) + "."
                    + chunkMetadata.getNumber() + ", space remaining is " + currentSpace/1000
                    + "kB, total space is " + initialSpace/1000 + "kB.");
        }
        return recoveredSpace;
    }

    public void run() {
        multicastControl.start();
        multicastBackup.start();
        multicastRestore.start();
        if (Arrays.equals(version, new int[]{1, 3}) || Arrays.equals(version, new int[]{2, 0}))                         // if this is version 1.3 - enhancement 3 or 2.0 - all enhancements
            instance.checkChunks_v_1_3();
        service.start();
        int sleepTime;
        while (!stop) {
            try {
                saveFileMetadatas();
                saveChunkMetadatas();
                sleepTime = TIME_BETWEEN_SAVES;
            }
            catch (ConcurrentModificationException e) {
                sleepTime = SLEEP_TIME;
            }
            try {
                sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        saveFileMetadatas();                                                                                            // save one last time before closing
        saveChunkMetadatas();
    }

    private void close() {
        System.out.println("Stopping Multicast...");
        multicastControl.close();
        multicastBackup.close();
        multicastRestore.close();
        service.close();
        stop = true;
    }

}

