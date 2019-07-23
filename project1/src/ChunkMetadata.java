import java.io.Serializable;
import java.util.HashSet;

class ChunkMetadata implements Serializable {

    private final String fileId;
    private final int number;
    private final int size;
    private final int minRepDeg;
    private final HashSet<Integer> peerIds;
    transient private boolean sendChunk = false;
    transient private boolean sendPutchunk = false;
    transient private boolean backupDone = false;
    transient private boolean restoreDone = false;
    transient private boolean changed = false;
    transient private boolean stored = false;                                                                           // only used in version 1.1 - enhancement 1
    transient private boolean notDeleted = false;                                                                       // only used in version 1.3 - enhancement 3
    transient private int numberPutchunks = 0;                                                                          // only used in version 1.4 - enhancement 4
    transient private boolean backupPutchunk = false;                                                                   // only used in version 1.4 - enhancement 4

    ChunkMetadata(String fileId, int number, int size, int minRepDeg) {
        this.fileId = fileId;
        this.number = number;
        this.size = size;
        this.minRepDeg = minRepDeg;
        peerIds = new HashSet<>();
    }

    String getPath() {
        Peer peer = Peer.getInstance();
        return "chunks" + peer.getServerId() + "/" + fileId + "." + number;
    }

    String getFileId() {
        return fileId;
    }

    int getNumber() {
        return number;
    }

    int getSize() {
        return size;
    }

    int getRepDeg() {
        return peerIds.size();
    }

    int getMinRepDeg() {
        return minRepDeg;
    }

    boolean getSendChunk() {
        return sendChunk;
    }

    void setSendChunk(boolean sendChunk) {
        this.sendChunk = sendChunk;
    }

    boolean getSendPutchunk() {
        return sendPutchunk;
    }

    void setSendPutchunk(boolean sendPutchunk) {
        this.sendPutchunk = sendPutchunk;
    }

    boolean getBackupDone() {
        return backupDone;
    }

    void setBackupDone() {
        backupDone = true;
    }

    boolean getRestoreDone() {
        return restoreDone;
    }

    void setRestoreDone() {
        restoreDone = true;
    }

    void setChanged(boolean changed) {
        this.changed = changed;
    }

    boolean getChanged() {
        return changed;
    }

    void setStored() {
        stored = true;
    }

    boolean getStored() {
        return stored;
    }

    boolean getNotDeleted() {                                                                                           // only used in version 1.3 - enhancement 3
        return notDeleted;
    }

    void setNotDeleted() {                                                                                              // only used in version 1.3 - enhancement 3
        notDeleted = true;
    }

    int getNumberPutchunks() {                                                                                          // only used in version 1.4 - enhancement 4
        return numberPutchunks;
    }

    void incNumberPutchunks() {                                                                                         // only used in version 1.4 - enhancement 4
        numberPutchunks++;
        backupPutchunk = false;
    }

    void resetBackupPutchunk() {                                                                                        // only used in version 1.4 - enhancement 4
        numberPutchunks = 0;
        backupPutchunk = false;
    }

    boolean getBackupPutchunk() {                                                                                       // only used in version 1.4 - enhancement 4
        return backupPutchunk;
    }

    void setBackupPutchunk() {                                                                                          // only used in version 1.4 - enhancement 4
        backupPutchunk = true;
    }

    synchronized void addPeerId(int senderId) {
        peerIds.add(senderId);
    }

    void removePeerId(int senderId) {
        peerIds.remove(senderId);
    }

    void resetPeerIds() {
        peerIds.clear();
    }

}
