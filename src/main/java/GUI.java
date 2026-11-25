import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// Swing view. All public view updates are safe to call from network threads.
public final class GUI extends JFrame {
    public interface Handler {
        void send(String message);

        void close();
    }

    // Palette
    private static final Color BG = new Color(243, 245, 249);
    private static final Color SURFACE = Color.WHITE;
    private static final Color FIELD_BG = new Color(246, 248, 251);
    private static final Color FIELD_OFF = new Color(236, 239, 244);
    private static final Color ACCENT = new Color(37, 87, 174);
    private static final Color ACCENT_HOVER = new Color(48, 105, 196);
    private static final Color ACCENT_PRESSED = new Color(28, 70, 146);
    private static final Color ACCENT_OFF = new Color(170, 184, 210);
    private static final Color TEXT = new Color(28, 34, 45);
    private static final Color MUTED = new Color(110, 120, 135);
    private static final Color PLACEHOLDER = new Color(150, 158, 170);
    private static final Color BORDER = new Color(218, 224, 232);
    private static final Color OK = new Color(34, 160, 90);
    private static final Color WARN = new Color(230, 160, 30);
    private static final Color BAD = new Color(210, 60, 60);
    private static final Color[] SENDER_COLORS = {
            new Color(186, 73, 52), new Color(32, 138, 104), new Color(142, 84, 190),
            new Color(196, 120, 20), new Color(18, 130, 160), new Color(190, 60, 120)
    };

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_MESSAGES = 500;
    private static final String HINT = "Type a message and press Enter";

    private final String name;
    private final ChatPanel chat = new ChatPanel();
    private final JScrollPane scroll;
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final StatusDot dot = new StatusDot();
    private final JLabel status = new JLabel("Connecting…");
    private final PlaceholderField input = new PlaceholderField(HINT);
    private final RoundedButton sendButton = new RoundedButton("Send");

    private boolean connected = true;
    private String lastSender;

    public GUI(String name, Handler handler) {
        super("Multicast Chat · " + name);
        this.name = name;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(420, 360));
        setSize(780, 580);
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        setContentPane(root);

        Font base = UIManager.getFont("Label.font");
        if (base == null)
            base = new JLabel().getFont();

        // Header: title and connection status on the left, your identity on the right
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                new EmptyBorder(14, 20, 14, 20)));

        JLabel title = new JLabel("Multicast Chat");
        title.setFont(base.deriveFont(Font.BOLD, 20f));
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        status.setFont(base.deriveFont(12.5f));
        status.setForeground(MUTED);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.add(dot);
        statusRow.add(status);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(title);
        left.add(statusRow);
        header.add(left, BorderLayout.WEST);

        JPanel chip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        chip.setOpaque(false);
        JLabel me = new JLabel(name);
        me.setFont(base.deriveFont(Font.BOLD, 13f));
        me.setForeground(TEXT);
        me.setToolTipText("You are chatting as " + name);
        chip.add(me);
        chip.add(new Avatar(name, 30));
        header.add(chip, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // Center: empty state until the first message arrives
        scroll = new JScrollPane(chat, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(BG);
        JPanel emptyText = new JPanel();
        emptyText.setOpaque(false);
        emptyText.setLayout(new BoxLayout(emptyText, BoxLayout.Y_AXIS));
        JLabel emptyTitle = new JLabel("No messages yet");
        emptyTitle.setFont(base.deriveFont(Font.BOLD, 16f));
        emptyTitle.setForeground(TEXT);
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel emptyHint = new JLabel("Messages from everyone in the group show up here.");
        emptyHint.setFont(base.deriveFont(13f));
        emptyHint.setForeground(MUTED);
        emptyHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyText.add(emptyTitle);
        emptyText.add(Box_gap(6));
        emptyText.add(emptyHint);
        empty.add(emptyText, new GridBagConstraints());

        center.setBackground(BG);
        center.add(empty, "empty");
        center.add(scroll, "chat");
        cards.show(center, "empty");
        root.add(center, BorderLayout.CENTER);

        // Composer
        JPanel composer = new JPanel(new BorderLayout(10, 0));
        composer.setBackground(SURFACE);
        composer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                new EmptyBorder(12, 16, 12, 16)));
        input.setFont(base.deriveFont(15f));
        input.setToolTipText("Press Enter to send, Esc to clear");
        composer.add(input, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        root.add(composer, BorderLayout.SOUTH);

        Runnable send = () -> {
            String value = input.getText().trim();
            if (!connected || value.isEmpty())
                return;
            handler.send(value);
            input.setText("");
        };
        input.addActionListener(event -> send.run());
        sendButton.addActionListener(event -> send.run());
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateSendState();
            }

            public void removeUpdate(DocumentEvent e) {
                updateSendState();
            }

            public void changedUpdate(DocumentEvent e) {
                updateSendState();
            }
        });
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        input.getActionMap().put("clear", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                input.setText("");
            }
        });
        updateSendState();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                input.requestFocusInWindow();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                handler.close();
            }
        });
    }

    public void queueMessage(String message) {
        onUiThread(() -> addMessage(message));
    }

    public void setStatus(String text) {
        onUiThread(() -> status.setText(text));
    }

    public void setConnected(boolean connected) {
        onUiThread(() -> {
            this.connected = connected;
            dot.setColor(connected ? OK : BAD);
            input.setEnabled(connected);
            input.setPlaceholder(connected ? HINT : "Not connected");
            updateSendState();
            if (connected)
                input.requestFocusInWindow();
        });
    }

    private void updateSendState() {
        sendButton.setEnabled(connected && !input.getText().trim().isEmpty());
    }

    // Message format is "sender: text". Anything else is shown as a centered system
    // line.
    private void addMessage(String raw) {
        JScrollBar bar = scroll.getVerticalScrollBar();
        boolean nearBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 48;

        int idx = raw.indexOf(": ");
        boolean fromUser = idx > 0 && idx <= 32;
        String sender = fromUser ? raw.substring(0, idx).trim() : "";
        String text = fromUser ? raw.substring(idx + 2) : raw;

        Row row;
        boolean mine = false;
        if (!fromUser) {
            JLabel line = new JLabel(text, SwingConstants.CENTER);
            line.setFont(line.getFont().deriveFont(12f));
            line.setForeground(MUTED);
            row = new Row(line, null, BorderLayout.CENTER, 8);
            lastSender = null;
        } else {
            mine = sender.equals(name);
            boolean sameAsPrevious = sender.equals(lastSender);
            Bubble bubble = new Bubble(sender, text, mine, !sameAsPrevious, LocalTime.now().format(TIME));
            bubble.setLimit(chat.limit());
            row = new Row(bubble, bubble, mine ? BorderLayout.EAST : BorderLayout.WEST, sameAsPrevious ? 1 : 8);
            lastSender = sender;
        }

        if (chat.getComponentCount() >= MAX_MESSAGES)
            chat.remove(0);
        chat.add(row);
        cards.show(center, "chat");
        chat.revalidate();
        chat.repaint();

        // Follow new messages only if you are already at the bottom, or you sent it
        if (nearBottom || mine)
            SwingUtilities.invokeLater(() -> bar.setValue(bar.getMaximum()));
    }

    private static Component Box_gap(int height) {
        return javax.swing.Box.createVerticalStrut(height);
    }

    private static Color senderColor(String sender) {
        return SENDER_COLORS[Math.floorMod(sender.hashCode(), SENDER_COLORS.length)];
    }

    private static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void onUiThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread())
            action.run();
        else
            SwingUtilities.invokeLater(action);
    }

    // Scrollable column of rows that always matches the viewport width
    private static final class ChatPanel extends JPanel implements Scrollable {
        ChatPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(new EmptyBorder(12, 16, 12, 16));
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    int limit = limit();
                    for (Component c : getComponents()) {
                        if (c instanceof Row && ((Row) c).bubble != null)
                            ((Row) c).bubble.setLimit(limit);
                    }
                    revalidate();
                }
            });
        }

        // Max text width inside a bubble: about 72 percent of the chat width
        int limit() {
            int w = getWidth() - 32;
            if (w <= 0)
                w = 520;
            return Math.max(160, ((int) (w * 0.72) - 24) / 8 * 8);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 32);
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class Row extends JPanel {
        final Bubble bubble;

        Row(JComponent content, Bubble bubble, String constraint, int topGap) {
            super(new BorderLayout());
            this.bubble = bubble;
            setOpaque(false);
            setBorder(new EmptyBorder(topGap, 0, 1, 0));
            add(content, constraint);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    // Rounded message bubble. Your messages are blue and right aligned.
    private static final class Bubble extends JPanel {
        private final boolean mine;
        private final String text;
        private final JLabel body = new JLabel();
        private int limit = -1;

        Bubble(String sender, String text, boolean mine, boolean showSender, String time) {
            super(new BorderLayout(0, 2));
            this.mine = mine;
            this.text = text;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 6, 12));

            Font base = UIManager.getFont("Label.font");
            if (base == null)
                base = body.getFont();
            body.setFont(base.deriveFont(14.5f));
            body.setForeground(mine ? Color.WHITE : TEXT);
            add(body, BorderLayout.CENTER);

            if (showSender && !mine) {
                JLabel who = new JLabel(sender);
                who.setFont(base.deriveFont(Font.BOLD, 12f));
                who.setForeground(senderColor(sender));
                add(who, BorderLayout.NORTH);
            }

            JLabel when = new JLabel(time);
            when.setFont(base.deriveFont(11f));
            when.setForeground(mine ? new Color(205, 220, 245) : MUTED);
            when.setHorizontalAlignment(SwingConstants.RIGHT);
            add(when, BorderLayout.SOUTH);
        }

        void setLimit(int newLimit) {
            if (newLimit == limit)
                return;
            limit = newLimit;
            FontMetrics fm = body.getFontMetrics(body.getFont());
            int natural = 0;
            for (String line : text.split("\n", -1))
                natural = Math.max(natural, fm.stringWidth(line));
            String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
            if (natural + 2 <= newLimit)
                body.setText("<html>" + html + "</html>");
            else
                body.setText("<html><body style='width:" + newLimit + "px'>" + html + "</body></html>");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(mine ? ACCENT : SURFACE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
            if (!mine) {
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 18, 18));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class StatusDot extends JComponent {
        private Color color = WARN;

        StatusDot() {
            setPreferredSize(new Dimension(16, 12));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(color);
            g2.fillOval(0, (getHeight() - 9) / 2, 9, 9);
            g2.dispose();
        }
    }

    private static final class Avatar extends JComponent {
        private final String initial;
        private final Color color;
        private final int size;

        Avatar(String name, int size) {
            this.initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            this.color = senderColor(name);
            this.size = size;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(color);
            g2.fillOval(0, 0, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, size * 0.46f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(initial, (size - fm.stringWidth(initial)) / 2,
                    (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // Rounded input with a placeholder and a focus ring
    private static final class PlaceholderField extends JTextField {
        private String placeholder;

        PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setBorder(new EmptyBorder(9, 16, 9, 16));
            setCaretColor(TEXT);
            setForeground(TEXT);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(isEnabled() ? FIELD_BG : FIELD_OFF);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
            boolean focused = isFocusOwner();
            g2.setColor(focused ? ACCENT : BORDER);
            g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
            g2.draw(new RoundRectangle2D.Float(0.8f, 0.8f, getWidth() - 1.6f, getHeight() - 1.6f, 24, 24));
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && placeholder != null) {
                Graphics2D p = (Graphics2D) g.create();
                smooth(p);
                p.setColor(PLACEHOLDER);
                p.setFont(getFont());
                FontMetrics fm = p.getFontMetrics();
                Insets in = getInsets();
                p.drawString(placeholder, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                p.dispose();
            }
        }
    }

    // Custom painted button so the accent color shows on every look and feel
    private static final class RoundedButton extends JButton {
        private boolean hover;

        RoundedButton(String label) {
            super(label);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(Color.WHITE);
            setFont(getFont().deriveFont(Font.BOLD, 14f));
            setBorder(new EmptyBorder(9, 22, 9, 22));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 84), Math.max(d.height, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Color fill = !isEnabled() ? ACCENT_OFF
                    : getModel().isPressed() ? ACCENT_PRESSED
                            : hover ? ACCENT_HOVER : ACCENT;
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
