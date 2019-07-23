class Putchunk extends Message {

    private static final int MAX_ATTEMPTS = 5;
    private static final int SLEEP_TIME = 1000;

    Putchunk(int[] version, int senderId, String fileId, int chunkNo, int replicationDeg, byte[] body) {
        super(MessageType.PUTCHUNK, version, senderId, fileId, chunkNo, replicationDeg, body);
    }

    public void run() {
        boolean done = false;
        int sleepTime = SLEEP_TIME;
        int attempts = 0;
        Peer peer = Peer.getInstance();
        while (!done && attempts < MAX_ATTEMPTS) {
            peer.getMulticastBackup().send(this);
            try {
                sleep(sleepTime);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            done = peer.checkRepDeg(getFileId(), getChunkNo());
            sleepTime *= 2;
            attempts++;
        }
        if (peer.myBackupFile(getFileId())) peer.setChunkBackupDone(getFileId(), getChunkNo());
    }

}
