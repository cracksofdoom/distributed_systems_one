import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {
    @Test void retainsOnlyLast1024Messages() {
        History history = new History();
        for (int i = 1; i <= 1025; i++) history.addMessage(new Message(i, "A", new byte[] {1}, i));
        assertEquals(1024, history.size());
        assertNull(history.findMessage(1));
        assertNotNull(history.findMessage(2));
        assertNotNull(history.findMessage(1025));
    }
}
