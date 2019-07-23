import java.net.DatagramPacket;

class StoredMessage extends Message {

    StoredMessage(DatagramPacket packet) {
        super(packet);
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (getSenderId() == peer.getServerId()) return;                                                                // if this message is mine, do nothing
        if (peer.myBackupFile(getFileId())) {                                                                           // if this is a chunk of one of my files
            FileMetadata fileMetadata = peer.getFileMetadata(getFileId());
            fileMetadata.addPeerId(getChunkNo(), getSenderId());                                                        // add the peer id to the file metadata
            fileMetadata.setChanged(true);
            return;
        }
        if (!peer.storedChunk(getFileId() + getChunkNo())) return;                                                      // if the chunk is not stored, do nothing
        ChunkMetadata chunkMetadata = peer.getChunkMetadata(getFileId() + getChunkNo());
        chunkMetadata.addPeerId(getSenderId());                                                                         // otherwise add PeerId to chunk metadata
        chunkMetadata.setChanged(true);
    }

}
