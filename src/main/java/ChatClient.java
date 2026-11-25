import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class ChatClient {
    private final String name;
    private final Sequencer sequencer;
    private final Group group;
    private final GUI gui;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chat-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong messageId = new AtomicLong();
    private volatile long nextSequence;

    private ChatClient(String name, Sequencer sequencer, SequencerJoinInfo info, int multicastPort) throws Exception {
        this.name = name;
        this.sequencer = sequencer;
        nextSequence = info.sequence;
        gui = new GUI(name, new GUI.Handler() {
            @Override
            public void send(String message) {
                worker.execute(() -> sendMessage(message));
            }

            @Override
            public void close() {
                shutdown();
            }
        });
        gui.setConnected(false);
        group = new Group(info.addr, multicastPort, this::receive,
                e -> gui.setStatus("Network error: " + e.getMessage()));
        Thread receiver = new Thread(group, "multicast-receiver");
        receiver.setDaemon(true);
        receiver.start();
        worker.execute(this::recoverAfterJoin);
        gui.setStatus("Connected as " + name + " · " + info.addr.getHostAddress());
        gui.setConnected(true);
        gui.setVisible(true);
    }

    private void sendMessage(String value) {
        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            sequencer.send(name, bytes, messageId.incrementAndGet());
        } catch (Exception e) {
            gui.setStatus("Send failed: " + e.getMessage());
        }
    }

    private synchronized void receive(Message message) {
        try {
            while (nextSequence < message.getLastSequence()) {
                try {
                    Message missing = Message.fromByteStream(sequencer.getMissing(name, nextSequence));
                    display(missing);
                    nextSequence++;
                } catch (SequencerException unavailable) {
                    gui.queueMessage("Messages #" + nextSequence + "–#" + (message.getLastSequence() - 1)
                            + " are no longer available");
                    nextSequence = message.getLastSequence();
                }
            }
            if (message.getLastSequence() == nextSequence) {
                display(message);
                nextSequence++;
            }
        } catch (Exception e) {
            gui.setStatus("Recovery failed at #" + nextSequence + ": " + e.getMessage());
        }
    }

    private synchronized void recoverAfterJoin() {
        try {
            while (true) {
                Message missing = Message.fromByteStream(sequencer.getMissing(name, nextSequence));
                display(missing);
                nextSequence++;
            }
        } catch (SequencerException caughtUp) {
            // No more messages were published during the join window.
        } catch (Exception e) {
            gui.setStatus("Recovery failed at #" + nextSequence + ": " + e.getMessage());
        }
    }

    private void display(Message message) {
        gui.queueMessage("#" + message.getLastSequence() + "  " + message.getSender() + "  ·  "
                + new String(message.getMsg(), StandardCharsets.UTF_8));
    }

    private void shutdown() {
        group.close();
        worker.execute(() -> {
            try {
                sequencer.leave(name);
            } catch (Exception e) {
                System.err.println("Unable to leave: " + e.getMessage());
            }
        });
        worker.shutdown();
    }

    public static void main(String[] args) throws Exception {
        AppOptions options = new AppOptions(args);
        String name = options.get("--name", System.getProperty("user.name", "guest")).trim();
        String host = options.get("--host", "localhost");
        int port = options.port("--port", 1099);
        int multicastPort = options.port("--multicast-port", 6789);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        try {
            Sequencer sequencer = (Sequencer) LocateRegistry.getRegistry(host, port).lookup(SequencerServer.SERVICE);
            SequencerJoinInfo info = sequencer.join(name);
            SwingUtilities.invokeLater(() -> {
                try {
                    new ChatClient(name, sequencer, info, multicastPort);
                } catch (Exception e) {
                    try {
                        sequencer.leave(name);
                    } catch (Exception ignored) {
                    }
                    JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to connect", JOptionPane.ERROR_MESSAGE);
                }
            });
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Unable to connect", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
