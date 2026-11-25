import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** A bounded wire format for one ordered multicast message. */
public final class Message {
    public static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_SENDER_BYTES = 64;
    private static final int VERSION = 1;

    private final long id;
    private final String sender;
    private final byte[] body;
    private final long sequence;

    public Message(long id, String sender, byte[] body, long sequence) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(body, "body");
        int senderBytes = sender.getBytes(StandardCharsets.UTF_8).length;
        if (sender.isBlank() || senderBytes > MAX_SENDER_BYTES) {
            throw new IllegalArgumentException("Sender must be 1 to 64 UTF-8 bytes");
        }
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Message must be 1 to 4096 bytes");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive");
        }
        this.id = id;
        this.sender = sender;
        this.body = body.clone();
        this.sequence = sequence;
    }

    public long getMsgID() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public byte[] getMsg() {
        return body.clone();
    }

    public long getLastSequence() {
        return sequence;
    }

    public static byte[] toByteStream(Message message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            byte[] name = message.sender.getBytes(StandardCharsets.UTF_8);
            out.writeByte(VERSION);
            out.writeLong(message.sequence);
            out.writeLong(message.id);
            out.writeShort(name.length);
            out.write(name);
            out.writeShort(message.body.length);
            out.write(message.body);
        }
        return bytes.toByteArray();
    }

    public static Message fromByteStream(byte[] data) throws IOException {
        return fromByteStream(data, data.length);
    }

    public static Message fromByteStream(byte[] data, int length) throws IOException {
        if (length < 0 || length > data.length || length > MAX_BODY_BYTES + MAX_SENDER_BYTES + 21) {
            throw new IOException("Invalid datagram length");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, length))) {
            if (in.readUnsignedByte() != VERSION)
                throw new IOException("Unsupported message version");
            long sequence = in.readLong();
            long id = in.readLong();
            int nameLength = in.readUnsignedShort();
            if (nameLength < 1 || nameLength > MAX_SENDER_BYTES)
                throw new IOException("Invalid sender length");
            byte[] name = new byte[nameLength];
            in.readFully(name);
            int bodyLength = in.readUnsignedShort();
            if (bodyLength < 1 || bodyLength > MAX_BODY_BYTES)
                throw new IOException("Invalid body length");
            byte[] body = new byte[bodyLength];
            in.readFully(body);
            if (in.available() != 0)
                throw new IOException("Trailing message data");
            return new Message(id, new String(name, StandardCharsets.UTF_8), body, sequence);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message", e);
        }
    }
}
