import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    @Test void roundTripUsesOnlyReceivedDatagramLength() throws Exception {
        Message original = new Message(42, "Amina", "Hello 🌍".getBytes(StandardCharsets.UTF_8), 7);
        byte[] encoded = Message.toByteStream(original);
        byte[] reusedBuffer = Arrays.copyOf(encoded, encoded.length + 100);
        Message decoded = Message.fromByteStream(reusedBuffer, encoded.length);
        assertEquals(42, decoded.getMsgID());
        assertEquals(7, decoded.getLastSequence());
        assertEquals("Amina", decoded.getSender());
        assertArrayEquals(original.getMsg(), decoded.getMsg());
        assertThrows(IOException.class, () -> Message.fromByteStream(reusedBuffer));
    }

    @Test void rejectsMalformedAndOversizedMessages() throws Exception {
        byte[] encoded = Message.toByteStream(new Message(1, "A", new byte[] {1}, 1));
        encoded[0] = 99;
        assertThrows(IOException.class, () -> Message.fromByteStream(encoded));
        assertThrows(IllegalArgumentException.class, () -> new Message(1, "A", new byte[4097], 1));
    }

    @Test void bodyIsDefensivelyCopied() {
        byte[] body = {1};
        Message message = new Message(1, "A", body, 1);
        body[0] = 2;
        message.getMsg()[0] = 3;
        assertArrayEquals(new byte[] {1}, message.getMsg());
    }
}
