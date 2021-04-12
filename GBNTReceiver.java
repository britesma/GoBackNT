import transport.Receiver;
import transport.TimeoutAction;

import java.util.HashMap;
import java.util.Map;

public class GBNTReceiver extends Receiver{

    private byte lastAck = (byte) -128;
    private boolean lastWasBad = false;

    private static final long TIMEOUT = 1000;

    private final Map<Byte, TimeoutAction> timeouts = new HashMap<> ();

    public GBNTReceiver() {
        prepTimeout((byte) -127);
    }

    @Override
    protected void unreliableReceive(byte[] data, int offset, int length) {
        if (length > data.length || length < 1) {
            //error
            return;
        }

        final byte sequenceN = data[offset];
        if (sequenceN == lastAck + 1) {
            //inivio ack visto che è buono
            lastWasBad = false;

            //cancel timeout since its good
            final TimeoutAction toCancel = timeouts.get(sequenceN);
            if (toCancel != null) {
                cancelTimeout(toCancel);
            }

            //send ack
            sendAck(sequenceN);
            lastAck++;
            System.out.println("RECEIVER: Sending ACK [" + lastAck + "]");

            //prepare next packet
            prepTimeout((byte) (sequenceN + 1));

            if (length > 1) {
                deliver(data, offset + 1, length - 1);
            } else {
                //error
            }
        } else if (lastAck < sequenceN) {
            System.err.println("RECEIVER: Bad packet received [" + (lastAck + 1) +  "], got [" + sequenceN + "]");
            if (!lastWasBad) {
               sendAck(lastAck);
            }
            lastWasBad = true;
        } else {
            //error
        }
    }

    private void prepTimeout(byte sequenceN) {
        final TimeoutAction timeout = () -> onTimeout(sequenceN);
        timeouts.put(sequenceN, timeout);
        setTimeout(TIMEOUT, timeout);
    }

    private void onTimeout(int sequenceN) {
        System.err.println("RECEIVER: Timeout for " + sequenceN);
        sendAck((byte) (sequenceN - 1));
    }

    private void sendAck(byte ackN) {
        //take the first byte for the ack number
        byte[] ack = new byte[1];
        ack[0] = ackN;
        //call unreliableSend with the ack number
        unreliableSend(ack, 0, 1);
    }

}
