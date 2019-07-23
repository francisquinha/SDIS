package peer;

import utils.Utils;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class PeerStart {
    /**
     * Key
     * Host to connect to
     *
     * @param args command line arguments to invoke PeerStart
     */
    public static void main(String[] args) {
        try {
            Peer p;
            if (args.length > 5 || args.length < 4) {
                System.out.println("Invalid peer invokation.\nInvoke as: PeerStart [name] join [known_host] [known_host_key] [space]\nOr: PeerStart [name] create [max_peers] [space].");
                return;
            }
            String peer_name = args[0];
            if (args[1].equals("join") && args.length >= 5) {
                InetAddress a;
                try {
                    a = InetAddress.getByName(args[2]);
                } catch (UnknownHostException e) {
                    System.out.println("Address:" + args[2] + " is not a valid IP address.\nStopping execution.");
                    return;
                }
                int g;
                try {
                    g = Integer.parseInt(args[3]);
                    if (g < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Provided known_host_guid is not a positive integer.\nStopping execution.");
                    return;
                }
                int space;
                if (args.length == 6) {
                    int k;
                    try {
                        k = Integer.parseInt(args[4]);
                        space = Integer.parseInt(args[5]);
                    } catch (NumberFormatException e) {
                        System.out.println("Provided key or space are not numbers.\nStopping execution.");
                        return;
                    }
                    p = new Peer(peer_name,a, g, k, space);
                } else {
                    try {
                        space = Integer.parseInt(args[4]);
                    } catch (NumberFormatException e) {
                        System.out.println("Provided key or space are not numbers.\nStopping execution.");
                        return;
                    }
                    p = new Peer(peer_name,a, g, space);
                }
            } else if (args[1].equals("create") && args.length == 4) {
                int n;
                try {
                    n = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    System.out.println("Provided max_peers is not a number.\nStopping execution.");
                    return;
                }
                if (!Utils.isPowerOfTwo(n)) {
                    System.out.println("Provided max_peers is not a power of 2.\nStopping execution.");
                    return;
                }
                int space = 0;
                try {
                    space = Integer.parseInt(args[3]);
                    if(space <= 0){
                    	throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Provided max_peers is not a number or lower than 0.\nStopping execution.");
                    return;
                }
                p = new Peer(peer_name,n, space);
            } else {
                System.out.println("Invalid peer invokation.\nInvoke as: PeerStart join known_host known_host_guid [key]\nOr: PeerStart create max_peers.");
                return;
            }
            p.start();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

}
