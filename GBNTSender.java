import transport.Sender;
import transport.TimeoutAction;
import java.util.HashMap;
import java.util.Map;

public class GBNTSender extends Sender {

    private static final int WINDOW = 4;

    private static final Object lock = new Object();

    private final Map<Integer, byte[]> segments = new HashMap<>();
    private final Map<Integer, TimeoutAction> timeouts = new HashMap<>();
    private final Map<Integer, Long> departs = new HashMap<>();
    //private final Map<Integer, Long> rtt = new HashMap<>();
    private long depart = 0;

    private int base = -127;
    private int nextSequenceN = -127;

    //EWMA
    private double alpha = 0.125;
    private double beta = 0.25;
    // last Round trip time computed
    private long lastRTT = 0;
    // last deviation of RTT average computed
    private long lastDevRTT = 0;
    // last RTT average computed, by default I set it to 1000
    private long lastAvgRTT = 1000;

    @Override
    public void close() {
       System.out.println("CLOSE ");
        while(base < nextSequenceN) {
            sendPacket(segments.get(base), base);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {

            }
        }
        segments.clear();
    }

    @Override
    protected int reliableSend(byte[] data, int offset, int length) {
        //compute data to deliver
        final int dataLength = offset + length;

        int count = 0;
        int begin = offset;

        while ((nextSequenceN < base + WINDOW) && (count < dataLength)) {
            synchronized (lock) {
                final int sequenceN = nextSequenceN;

                //check if there is something to send
                final int packet = Math.min(dataLength - begin + 1, MAX_PACKET_SIZE);
                if (packet <= 1) {
                    // there is nothing to send
                    break;
                }

                final byte[] packetData = new byte[packet];
                packetData[0] = (byte) sequenceN;
                //copy bytes from data to packetData
                System.arraycopy(data, begin, packetData, 1, packet - 1);

                //send the packet (packetData)
                segments.put(sequenceN, packetData);
                sendPacket(packetData, sequenceN);

                //update values begin and count
                begin += (packet - 1);
                count += (packet - 1);
                nextSequenceN++;
            }
        }

        //window is full, need to block sender, do not send other packets
        if (nextSequenceN == base + WINDOW) {
            blockSender();
        }
        return count;
    }

    private void sendPacket(byte[] packet, int sequenceN) {
        System.out.println("SENDER: Sending packet [sequence number: " + sequenceN + ", length: " + packet.length + "]");
        unreliableSend(packet, 0, packet.length);

        final TimeoutAction timeout = () -> {
            System.err.println("SENDER: Timeout for sequence number " + sequenceN);
            sendPacket(packet, sequenceN);
        };
        timeouts.put(sequenceN, timeout);
        //setTimeout passing value computed by the computeTimeout() function
        setTimeout(computeTimeout(), timeout);
        departs.put(sequenceN, System.currentTimeMillis());
        depart = System.currentTimeMillis();

    }

    @Override
    protected void unreliableReceive(byte[] data, int offset, int length) {
        final byte ackN = data[offset];
        // if ackN < base is not a valid ACK, otherwise is a valid ACK
        System.out.println("SENDER: ack number: " + ackN + "base: " + base);
        if (ackN >= base) {
            for (int i = base; i <= ackN; i++) {
                final TimeoutAction toCancel = timeouts.get(i);
                if (toCancel != null) {
                    timeouts.put(i, null);
                    cancelTimeout(toCancel);
                }
                final long arrive = System.currentTimeMillis();
                System.out.println("SENDER: depart: " + depart + " arrive: " + arrive + " RTT: " + (arrive - departs.get(i)));
                lastRTT = arrive - departs.get(i);

            }
            base = ackN + 1;
            resumeSender();
        } else {
            synchronized (lock) {
                // check the first to send and the last and
                // send back again all of them
                final int fts = Math.min(ackN + 1, base);
                final int lts = nextSequenceN - 1;

                for (int i = fts; i <= lts; i++) {
                    final byte[] pckt = segments.get(i);
                    if (pckt == null || pckt.length == 0) {
                        //error
                    } else {
                        System.out.println("SENDER: packet again n=" + i);
                        sendPacket(pckt, i);
                    }
                }
            }
        }
    }

    private long computeTimeout() {
        long measuredRTT = lastRTT;
        long avgRTT = (long) Math.ceil((1 - alpha) * lastAvgRTT + (alpha * measuredRTT));
        long devRTT = (long) Math.ceil((1 - beta) * lastDevRTT + (beta * Math.abs(avgRTT - measuredRTT)));
        //set the lastAvgRTT with the computed avgRTT and lastDevRTT with the computed devRTT
        lastAvgRTT = avgRTT;
        lastDevRTT = devRTT;
        System.out.println("SENDER: lastAvgRTT: " + lastAvgRTT + "lastDevRTT: " + lastDevRTT + "T: " + (long) Math.ceil(avgRTT + (4 * devRTT)));
        return (long) Math.ceil(avgRTT + (4 * devRTT));
    }
}
