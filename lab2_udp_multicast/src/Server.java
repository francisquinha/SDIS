import java.io.*;

public class Server {

    public static void main(String[] args) {
        BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\nENTER TO CLOSE SERVER\n");
        int servicePort = Integer.parseInt(args[0]);
        String multicastIP = args[1];
        int multicastPort = Integer.parseInt(args[2]);
        Service service = new Service(servicePort);
        String serviceIP = service.getServiceIP();
        Multicast multicast = new Multicast(servicePort, serviceIP, multicastPort, multicastIP);
        service.start();
        multicast.start();
        try {
            buffer.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        multicast.cancel();
        service.cancel();
    }

}