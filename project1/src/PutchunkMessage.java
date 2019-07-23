import java.net.DatagramPacket;
import java.util.Arrays;

class PutchunkMessage extends Message {

    private static final int SLEEP_TIME = 400;
    private static final int MAX_PUTCHUNK_ATTEMPTS = 5;
    private static final int PUTCHUNK_SLEEP_TIME = 1000;

    PutchunkMessage(DatagramPacket packet) {
        super(packet);
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (peer.myBackupFile(getFileId())) return;                                                                     // if the file is mine, do nothing
        int chunkSize = getBody().length;
        Message reply = new Message(MessageType.STORED, peer.getVersion(),
                peer.getServerId(), getFileId(), getChunkNo(), 0, null);
        if (Arrays.equals(peer.getVersion(), new int[]{1, 1})
                || Arrays.equals(peer.getVersion(), new int[]{2, 0})) {                                                 // if this is version 1.1 - enhancement 1 or 2.0 - all enhancements
            run_v_1_1(peer, reply, chunkSize);                                                                          // use enhancement 1 chunk store protocol
            return;
        }
        if (checkAlreadyStored(peer, reply, SLEEP_TIME)) return;                                                        // if the chunk is already stored, reply only
        if (!checkSpace(peer, chunkSize)) return;                                                                       // if the space is not enough even after safe chunk removal, do nothing
        if (peer.addChunk(getFileId(), getChunkNo(), getBody(), chunkSize, getReplicationDeg())) {                      // if the file was stored successfully, reply
            ChunkMetadata chunkMetadata = peer.getChunkMetadata(getFileId() + getChunkNo());
            chunkMetadata.addPeerId(peer.getServerId());
            if (Arrays.equals(peer.getVersion(), new int[]{1, 4})
                    || Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                               // if this is version 1.4 - enhancement 4 or 2.0 - all enhancements
                run_v_1_4(peer, chunkMetadata);                                                                         // use enhancement 4 putchunk backup protocol
            chunkMetadata.setChanged(true);
            waitReply(peer, reply, SLEEP_TIME);
        }
    }

    private boolean checkAlreadyStored(Peer peer, Message reply, int sleepTime) {
        if (peer.reallyStoredChunk(getFileId() + getChunkNo())) {
            ChunkMetadata chunkMetadata = peer.getChunkMetadata(getFileId() + getChunkNo());
            chunkMetadata.setSendPutchunk(false);                                                                       // if this putchunk message started due to a reclaim, do not send a putchunk message, somebody else already did it
            if (Arrays.equals(peer.getVersion(), new int[]{1, 4})
                    || Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                               // if this is version 1.4 - enhancement 4 or 2.0 - all enhancements
                run_v_1_4(peer, chunkMetadata);                                                                         // use enhancement 4 putchunk backup protocol
            waitReply(peer, reply, sleepTime);
            return true;
        }
        return false;
    }

    private boolean checkSpace(Peer peer, int chunkSize) {
        if (peer.getSpace() < chunkSize) {                                                                              // if the available space is not enough, try to delete chunks safely after small random wait
            try {
                sleep(peer.getRandom().nextInt(SLEEP_TIME));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            peer.removeChunks(chunkSize - peer.getSpace(), true);
        }
        return peer.getSpace() >= chunkSize;
    }

    private void waitReply(Peer peer, Message reply, int sleepTime) {
        try {
            sleep(peer.getRandom().nextInt(sleepTime));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        peer.getMulticastControl().send(reply);
    }

    private void run_v_1_1(Peer peer, Message reply, int chunkSize) {
        if (checkAlreadyStored(peer, reply, SLEEP_TIME/2)) return;                                                      // if the chunk is already stored, reply only
        if (peer.storedChunk(getFileId() + getChunkNo())) return;                                                       // if the chunk metadata (but not the chunk) is stored, do nothing
        if (!checkSpace(peer, chunkSize)) return;                                                                       // if the space is not enough even after safe chunk removal, do nothing
        ChunkMetadata chunkMetadata = peer.addChunkMetadata(getFileId(), getChunkNo(),                                  // create chunk metadata but do not store chunk
                chunkSize, getReplicationDeg());
        try {
            sleep(peer.getRandom().nextInt(SLEEP_TIME * 2));                                                            // wait for some random time
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (chunkMetadata.getRepDeg() >= chunkMetadata.getMinRepDeg())                                                  // if the replication degree is ok
            peer.removeChunkMetadata(getFileId(), getChunkNo());                                                        // remove metadata
        else if (!peer.storeChunk(chunkMetadata, getBody()))                                                            // if we cannot store the file
            peer.removeChunkMetadata(getFileId(), getChunkNo());                                                        // remove metadata
        else {
            chunkMetadata.addPeerId(peer.getServerId());                                                                // add myself to peer ids
            if (Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                                      // if this is version 2.0 - all enhancements
                run_v_1_4(peer, chunkMetadata);                                                                         // use enhancement 4 putchunk backup protocol
            peer.getMulticastControl().send(reply);
        }
    }

    private void run_v_1_4(Peer peer, ChunkMetadata chunkMetadata) {
        chunkMetadata.incNumberPutchunks();                                                                             // increment the number of putchunks received
        if (chunkMetadata.getNumberPutchunks() == 1) {                                                                  // if it was not done already (this was the first putchunk increment), start a new check putchunks thread
            Thread checkPutchunks = new Thread(() -> {checkPutchunks(peer, chunkMetadata);});
            checkPutchunks.start();
        }

    }

    private void checkPutchunks(Peer peer, ChunkMetadata chunkMetadata) {
        if (chunkMetadata.getNumberPutchunks() >= MAX_PUTCHUNK_ATTEMPTS) {                                              // if someone did all putchunk attempts, reset puchunk counter and do nothing else
            chunkMetadata.resetBackupPutchunk();
            return;
        }
        int sleepTime = 0;
        for (int i = 1; i <= 2 * MAX_PUTCHUNK_ATTEMPTS; i *= 2)
            sleepTime += PUTCHUNK_SLEEP_TIME * i;
        try {
            Thread.sleep(sleepTime);                                                                                    // wait enough time to make sure all putchunk attempts have been done by original peer
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (chunkMetadata.getNumberPutchunks() < MAX_PUTCHUNK_ATTEMPTS                                                  // if the number of putchunk messages is still not enough
                && chunkMetadata.getRepDeg() < chunkMetadata.getMinRepDeg()) {                                          // and the replication degree is less than it should be
            chunkMetadata.setBackupPutchunk();                                                                          // flag chunk metadata do show that I intend to start backup putchunk
            try {
                Thread.sleep(peer.getRandom().nextInt(SLEEP_TIME));                                                     // wait some random time to make sure nobody else does this
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!chunkMetadata.getBackupPutchunk()) {                                                                   // if someone else started the backup putchunk protocol (a new putchunk was received), do not do it
                chunkMetadata.resetBackupPutchunk();                                                                    // reset counter and boolean
                chunkMetadata.incNumberPutchunks();                                                                     // increment the number of putchunks because of the putchunk just received
                return;
            }
            byte[] chunk = peer.getChunk(chunkMetadata.getFileId() + chunkMetadata.getNumber());
            Putchunk putchunk = new Putchunk(peer.getVersion(), peer.getServerId(), chunkMetadata.getFileId(),          // restart putchunk protocol
                    chunkMetadata.getNumber(), chunkMetadata.getMinRepDeg(), chunk);
            putchunk.start();
            chunkMetadata.resetBackupPutchunk();                                                                        // reset counter and boolean
        }
    }

}
