import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Swing view. All public view updates are safe to call from network threads.
 */
public final class GUI extends JFrame {
    public interface Handler {
        void send(String message);

        void close();
    }

    private final javax.swing.DefaultListModel<String> messages = new javax.swing.DefaultListModel<>();
    private final JList<String> messageList = new JList<>(messages);
    private final JLabel status = new JLabel("Connecting…");
    private final JTextField input = new JTextField();
    private final JButton sendButton = new JButton("Send");

    public GUI(String name, Handler handler) {
        super("Multicast Chat · " + name);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(480, 360));
        setSize(760, 520);
        setLocationByPlatform(true);

        Color background = new Color(246, 248, 251);
        Color accent = new Color(37, 87, 174);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(background);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setContentPane(root);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Multicast Chat");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        status.setForeground(new Color(80, 90, 104));
        header.add(title, BorderLayout.NORTH);
        header.add(status, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        messageList.setFont(messageList.getFont().deriveFont(15f));
        messageList.setFixedCellHeight(30);
        messageList.setBackground(Color.WHITE);
        messageList.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JScrollPane scroll = new JScrollPane(messageList);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(218, 224, 232)));
        root.add(scroll, BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout(10, 0));
        composer.setOpaque(false);
        input.setFont(input.getFont().deriveFont(15f));
        input.setMargin(new Insets(8, 10, 8, 10));
        input.setToolTipText("Type a message and press Enter");
        sendButton.setBackground(accent);
        sendButton.setForeground(Color.WHITE);
        composer.add(input, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        root.add(composer, BorderLayout.SOUTH);

        Runnable send = () -> {
            String value = input.getText().trim();
            if (!value.isEmpty()) {
                handler.send(value);
                input.setText("");
            }
        };
        input.addActionListener(event -> send.run());
        sendButton.addActionListener(event -> send.run());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                handler.close();
            }
        });
    }

    public void queueMessage(String message) {
        onUiThread(() -> {
            messages.addElement(message);
            messageList.ensureIndexIsVisible(messages.size() - 1);
        });
    }

    public void setStatus(String text) {
        onUiThread(() -> status.setText(text));
    }

    public void setConnected(boolean connected) {
        onUiThread(() -> {
            input.setEnabled(connected);
            sendButton.setEnabled(connected);
            if (connected)
                input.requestFocusInWindow();
        });
    }

    private static void onUiThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread())
            action.run();
        else
            SwingUtilities.invokeLater(action);
    }
}
