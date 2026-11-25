import java.rmi.Remote;
import java.rmi.RemoteException;

/** Remote contract for the single ordering service. */
public interface Sequencer extends Remote {
    SequencerJoinInfo join(String sender) throws RemoteException, SequencerException;

    void send(String sender, byte[] message, long messageId) throws RemoteException;

    void leave(String sender) throws RemoteException;

    byte[] getMissing(String sender, long sequence) throws RemoteException, SequencerException;
}
