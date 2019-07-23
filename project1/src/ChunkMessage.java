import java.net.DatagramPacket;
import java.util.Arrays;

class ChunkMessage extends Message {

    ChunkMessage(DatagramPacket packet) {
        super(packet);
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (getSenderId() == peer.getServerId()) return;                                                                // if this message is mine, do nothing
        if (peer.myRestoreFile(getFileId())) {                                                                          // if this is a chunk of one of my files that are being restored
            peer.addRestoreChunks(getFileId(), getChunkNo(), getBody());
            peer.setChunkRestoreDone(getFileId(), getChunkNo());
            return;
        }
        if (!peer.storedChunk(getFileId() + getChunkNo())) return;                                                      // if the chunk is not stored, do nothing
        peer.getChunkMetadata(getFileId() + getChunkNo()).setSendChunk(false);                                          // else someone already replied to Getchunk, so do not reply to it
        if (Arrays.equals(peer.getVersion(), new int[]{1, 3})
                || Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                                   // if this is version 1.3 - enhancement 3 or version 2.0 - all enhancements
            peer.getChunkMetadata(getFileId() + getChunkNo()).setNotDeleted();                                          // set chunk as not deleted
    }

}
