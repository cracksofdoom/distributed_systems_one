import java.util.LinkedHashMap;
import java.util.Map;

/** Retains the most recent messages for recovery after packet loss. */
public final class History {
    private static final int CAPACITY = 1024;
    private final Map<Long, Message> messages = new LinkedHashMap<>();

    public synchronized void addMessage(Message message) {
        messages.put(message.getLastSequence(), message);
        if (messages.size() > CAPACITY) {
            messages.remove(messages.keySet().iterator().next());
        }
    }

    public synchronized Message findMessage(long sequenceNumber) {
        return messages.get(sequenceNumber);
    }

    public synchronized int size() {
        return messages.size();
    }
}
