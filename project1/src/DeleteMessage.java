import java.net.DatagramPacket;

class DeleteMessage extends Message {

    DeleteMessage(DatagramPacket packet) {
        super(packet);
    }

    public void run() {
        Peer peer = Peer.getInstance();
        if (getSenderId() == peer.getServerId()) return;                                                                // if this message is mine, do nothing
        peer.deleteFileChunks(getFileId());
    }

}
