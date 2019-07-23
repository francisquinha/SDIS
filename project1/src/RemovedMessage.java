import java.net.DatagramPacket;

class RemovedMessage extends Message {

    private static final int SLEEP_TIME = 400;

    RemovedMessage(DatagramPacket packet) {
        super(packet);
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (getSenderId() == peer.getServerId()) return;                                                                // if this message is mine, do nothing
        if (peer.myBackupFile(getFileId())) {                                                                           // if this is a chunk of one of my files, remove the PeerId from the metadata
            FileMetadata fileMetadata = peer.getFileMetadata(getFileId());
            fileMetadata.removePeerId(getChunkNo(), getSenderId());
            fileMetadata.setChanged(true);
        }
        if (!peer.storedChunk(getFileId() + getChunkNo())) return;                                                      // if the chunk is not stored, do nothing
        ChunkMetadata chunkMetadata = peer.getChunkMetadata(getFileId() + getChunkNo());
        chunkMetadata.removePeerId(getSenderId());
        chunkMetadata.setChanged(true);
        if (chunkMetadata.getRepDeg() >= chunkMetadata.getMinRepDeg()) return;                                          // if the replication degree is ok, do nothing else
        chunkMetadata.setSendPutchunk(true);                                                                            // assume we will have to initiate putchunk by setting sendPutchunk flag true
        byte[] chunk = peer.getChunk(getFileId() + getChunkNo());
        if (chunk == null) {                                                                                            // if chunk recover failed, remove it from the system and send removed message
            peer.removeNullChunk(getFileId(), getChunkNo());
            return;
        }
        try {
            sleep(peer.getRandom().nextInt(SLEEP_TIME));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (chunkMetadata.getSendPutchunk()) {                                                                          // if nobody else initiated putchunk until now, do it
            Putchunk putchunk = new Putchunk(peer.getVersion(), peer.getServerId(), getFileId(),
                    getChunkNo(), chunkMetadata.getMinRepDeg(), chunk);
            putchunk.start();

        }
        chunkMetadata.setSendPutchunk(false);
    }

}
