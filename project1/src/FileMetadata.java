import java.io.Serializable;
import java.util.HashMap;

class FileMetadata implements Serializable {

    private final String path;
    private final HashMap<Integer, ChunkMetadata> chunks;
    transient private boolean restore = false;
    transient private boolean backupDone = false;
    transient private boolean restoreDone = false;
    transient private boolean changed = false;

    FileMetadata(String path) {
        this.path = path;
        chunks = new HashMap<>();
    }

    String getPath() {
        return path;
    }

    int getRepDeg(int chunkNo) {
        return chunks.get(chunkNo).getRepDeg();
    }

    int getMinRepDeg(int chunkNo) {
        return chunks.get(chunkNo).getMinRepDeg();
    }

    boolean getRestore() {
        return restore;
    }

    void setRestore(boolean restore) {
        this.restore = restore;
    }

    boolean getBackupDone() {
        return backupDone;
    }

    void setBackupDone() {
        for (ChunkMetadata chunkMetada : chunks.values()) {
            if (!chunkMetada.getBackupDone()) {
                backupDone = false;
                return;
            }
        }
        backupDone = true;
    }

    void setRestoreDone() {
        for (ChunkMetadata chunkMetada : chunks.values()) {
            if (!chunkMetada.getRestoreDone()) {
                restoreDone = false;
                return;
            }
        }
        restoreDone = true;
    }

    boolean getRestoreDone() {
        return restoreDone;
    }

    void setChanged(boolean changed) {
        this.changed = changed;
    }

    boolean getChanged() {
        return changed;
    }

    String getRepDegrees() {
        String result = "";
        for (ChunkMetadata chunkMetada : chunks.values()) {
            result += chunkMetada.getRepDeg() + " ";
        }
        return result;
    }

    int getMinChunkRepDeg() {
        int result = Integer.MAX_VALUE;
        for (ChunkMetadata chunkMetada : chunks.values()) {
            if (chunkMetada.getRepDeg() < result)
                result = chunkMetada.getRepDeg();
        }
        return result;
    }

    synchronized void addChunk(String fileId, int chunkNo, int chunkSize, int minRepDeg) {
        ChunkMetadata chunkMetadata = new ChunkMetadata(fileId, chunkNo, chunkSize, minRepDeg);
        chunks.put(chunkNo, chunkMetadata);
    }

    void setChunkBackupDone(int chunkNo) {
        chunks.get(chunkNo).setBackupDone();
    }

    void setChunkRestoreDone(int chunkNo) {
        chunks.get(chunkNo).setRestoreDone();
    }

    synchronized void addPeerId(int chunkNo, int senderId) {
        chunks.get(chunkNo).addPeerId(senderId);
    }

    synchronized void removePeerId(int chunkNo, int senderId) {
        chunks.get(chunkNo).removePeerId(senderId);
    }

    int getNumberChunks() {
        return chunks.size();
    }

}
