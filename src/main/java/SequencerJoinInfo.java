import java.io.Serializable;
import java.net.InetAddress;

/** Multicast endpoint and first sequence a new client should display. */
public final class SequencerJoinInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    public final InetAddress addr;
    public final long sequence;

    public SequencerJoinInfo(InetAddress addr, long sequence) {
        this.addr = addr;
        this.sequence = sequence;
    }
}
