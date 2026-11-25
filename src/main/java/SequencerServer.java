import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;

public final class SequencerServer {
    public static final String SERVICE = "Sequencer";

    public static void main(String[] args) throws Exception {
        AppOptions options = new AppOptions(args);
        int port = options.port("--port", 1099);
        int servicePort = options.port("--service-port", 1100);
        int multicastPort = options.port("--multicast-port", 6789);
        String advertisedHost = options.get("--advertise-host", "");
        if (!advertisedHost.isBlank())
            System.setProperty("java.rmi.server.hostname", advertisedHost);
        InetAddress group = InetAddress.getByName(options.get("--group", "234.20.7.1"));
        SequencerImpl sequencer = new SequencerImpl(group, multicastPort);
        Registry registry = LocateRegistry.createRegistry(port);
        Sequencer stub = (Sequencer) UnicastRemoteObject.exportObject(sequencer, servicePort);
        registry.rebind(SERVICE, stub);
        System.out.printf("Sequencer ready: RMI ports %d/%d, multicast %s:%d%n", port, servicePort,
                group.getHostAddress(), multicastPort);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                registry.unbind(SERVICE);
                UnicastRemoteObject.unexportObject(sequencer, true);
                sequencer.close();
            } catch (Exception e) {
                System.err.println("Shutdown error: " + e.getMessage());
            }
        }));
        new CountDownLatch(1).await();
    }
}
