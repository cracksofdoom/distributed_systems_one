import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SequencerImplTest {
    @Test void assignsGlobalOrderAndAllowsRecovery() throws Exception {
        List<byte[]> published = new ArrayList<>();
        SequencerImpl sequencer = new SequencerImpl(InetAddress.getByName("234.20.7.1"), published::add);
        assertEquals(1, sequencer.join("Amina").sequence);
        assertEquals(1, sequencer.join("Ben").sequence);
        assertThrows(SequencerException.class, () -> sequencer.join("amina"));
        sequencer.send("Amina", new byte[] {1}, 1);
        sequencer.send("Ben", new byte[] {2}, 1);
        assertEquals(2, published.size());
        assertEquals(1, Message.fromByteStream(published.get(0)).getLastSequence());
        assertEquals(2, Message.fromByteStream(published.get(1)).getLastSequence());
        assertEquals(2, Message.fromByteStream(sequencer.getMissing("Amina", 2)).getLastSequence());
        assertEquals(3, sequencer.join("Chao").sequence);
        sequencer.leave("Amina");
        assertThrows(SequencerException.class, () -> sequencer.getMissing("Amina", 1));
    }

    @Test void failedPublishDoesNotAdvanceSequence() throws Exception {
        SequencerImpl sequencer = new SequencerImpl(InetAddress.getByName("234.20.7.1"), bytes -> { throw new java.io.IOException("offline"); });
        sequencer.join("Amina");
        assertThrows(java.rmi.RemoteException.class, () -> sequencer.send("Amina", new byte[] {1}, 1));
        assertEquals(1, sequencer.join("Ben").sequence);
    }
}
