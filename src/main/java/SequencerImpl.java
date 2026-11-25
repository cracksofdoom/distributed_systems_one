import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Assigns a single global sequence to all messages sent through this server.
 */
public final class SequencerImpl implements Sequencer, AutoCloseable {
    @FunctionalInterface
    interface Publisher {
        void publish(byte[] bytes) throws IOException;
    }

    private final InetAddress address;
    private final Publisher publisher;
    private final AutoCloseable resource;
    private final History history = new History();
    private final Set<String> senders = new HashSet<>();
    private long sequence;

    public SequencerImpl(InetAddress address, int port) throws IOException {
        if (!address.isMulticastAddress())
            throw new IllegalArgumentException("Group address must be multicast");
        MulticastSocket socket = new MulticastSocket();
        socket.setTimeToLive(1);
        this.address = address;
        this.publisher = bytes -> socket.send(new DatagramPacket(bytes, bytes.length, address, port));
        this.resource = socket;
    }

    SequencerImpl(InetAddress address, Publisher publisher) {
        this.address = address;
        this.publisher = publisher;
        this.resource = () -> {
        };
    }

    @Override
    public synchronized SequencerJoinInfo join(String sender) throws SequencerException {
        String name = normalize(sender);
        if (!senders.add(name))
            throw new SequencerException("Name is already in use");
        return new SequencerJoinInfo(address, sequence + 1);
    }

    @Override
    public synchronized void send(String sender, byte[] msg, long msgID) throws RemoteException {
        try {
            if (!senders.contains(normalize(sender)))
                throw new IllegalArgumentException("Join before sending");
            Message message = new Message(msgID, sender.trim(), msg, sequence + 1);
            byte[] bytes = Message.toByteStream(message);
            publisher.publish(bytes);
            sequence++;
            history.addMessage(message);
        } catch (Exception e) {
            throw new RemoteException("Unable to send message: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized void leave(String sender) {
        if (sender != null)
            senders.remove(sender.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public synchronized byte[] getMissing(String sender, long requested) throws SequencerException {
        if (!senders.contains(normalize(sender)))
            throw new SequencerException("Join before requesting history");
        Message message = history.findMessage(requested);
        if (message == null)
            throw new SequencerException("Sequence " + requested + " is no longer available");
        try {
            return Message.toByteStream(message);
        } catch (IOException e) {
            throw new SequencerException("Unable to encode history: " + e.getMessage());
        }
    }

    private static String normalize(String sender) throws SequencerException {
        if (sender == null || sender.isBlank()
                || sender.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 64) {
            throw new SequencerException("Name must be 1 to 64 UTF-8 bytes");
        }
        return sender.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() throws Exception {
        resource.close();
    }
}
