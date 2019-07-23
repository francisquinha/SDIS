import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

class GetchunkMessage extends Message {

    private static final int SLEEP_TIME = 400;
    private final InetAddress senderAddress;                                                                            // only used in version 1.2 - enhancement 2
    
    GetchunkMessage(DatagramPacket packet) {
        super(packet);
        senderAddress = packet.getAddress();
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (getSenderId() == peer.getServerId()) return;                                                                // if this message is mine, do nothing
        if (!peer.reallyStoredChunk(getFileId() + getChunkNo())) return;                                                // if the chunk is not stored, do nothing
        ChunkMetadata chunkMetadata = peer.getChunkMetadata(getFileId() + getChunkNo());
        chunkMetadata.setSendChunk(true);                                                                               // assume we will have to reply to this getchunk message by setting sendChunk flag true
        byte[] chunk = peer.getChunk(getFileId() + getChunkNo());
        if (chunk == null) {                                                                                            // if chunk recover failed, remove it from the system and send removed message
            peer.removeNullChunk(getFileId(), getChunkNo());
            return;
        }
        Message reply = new Message(MessageType.CHUNK, peer.getVersion(), peer.getServerId(),
                getFileId(), getChunkNo(), 0, chunk);
        try {
            sleep(peer.getRandom().nextInt(SLEEP_TIME));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (chunkMetadata.getSendChunk()) {                                                                             // if nobody else sent chunk message until now, send it
            if ((Arrays.equals(peer.getVersion(), new int[]{1, 2})
                || Arrays.equals(peer.getVersion(), new int[]{2, 0}))                                                   // if my version is 1.2 - enhancement 2 or 2.0 - all enhancements
                && (Arrays.equals(getVersion(), new int[]{1, 2})                                                        // and the initiator version is also 1.2 or 2.0
                || Arrays.equals(getVersion(), new int[]{2, 0}))) run_v_1_2(peer, reply);                               // use enhancement 2 protocol to reply
            else peer.getMulticastRestore().send(reply);
        }
        chunkMetadata.setSendChunk(false);
    }

    private void run_v_1_2(Peer peer, Message reply) {
        DatagramPacket sendPacket = new DatagramPacket(reply.getBytes(), reply.getBytes().length,
                senderAddress, getSenderId());
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.send(sendPacket);                                                                                    // try to send chunk using unicast channel to initiator peer
            socket.close();
            Message multicastReply = new Message(MessageType.CHUNK, peer.getVersion(), peer.getServerId(),              // also send empty body chunk message on multicast so that other peers back off
                    getFileId(), getChunkNo(), 0, new byte[0]);
            peer.getMulticastRestore().send(multicastReply);
        } catch (IOException e) {
            peer.getMulticastRestore().send(reply);                                                                     // if unicast response failed send multicast response with chunk
        }
    }

}
