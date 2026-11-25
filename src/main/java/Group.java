import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.Objects;
import java.util.function.Consumer;

/** Receives multicast packets until closed. */
public final class Group implements AutoCloseable, Runnable {
    private final MulticastSocket socket;
    private final InetSocketAddress address;
    private final Consumer<Message> handler;
    private final Consumer<Exception> errorHandler;

    public Group(InetAddress group, int port, Consumer<Message> handler, Consumer<Exception> errorHandler)
            throws IOException {
        if (!group.isMulticastAddress())
            throw new IllegalArgumentException("Group address must be multicast");
        this.handler = Objects.requireNonNull(handler);
        this.errorHandler = Objects.requireNonNull(errorHandler);
        address = new InetSocketAddress(group, port);
        socket = new MulticastSocket(port);
        try {
            socket.joinGroup(address, null);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[Message.MAX_BODY_BYTES + 128];
        while (!socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                handler.accept(Message.fromByteStream(packet.getData(), packet.getLength()));
            } catch (IOException e) {
                if (!socket.isClosed())
                    errorHandler.accept(e);
            } catch (RuntimeException e) {
                errorHandler.accept(e);
            }
        }
    }

    @Override
    public void close() {
        try {
            socket.leaveGroup(address, null);
        } catch (IOException ignored) {
            // Closing the socket still stops the receiver.
        }
        socket.close();
    }
}
