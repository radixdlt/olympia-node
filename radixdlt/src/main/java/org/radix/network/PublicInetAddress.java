package org.radix.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import com.radixdlt.universe.Universe;

public class PublicInetAddress {
    private static Random prng;
    private static InetAddress	localAddress;

    static
    {
        try {
        	localAddress = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
        	localAddress = InetAddress.getLoopbackAddress();
        }
    }

    private InetAddress confirmedAddress;
    private InetAddress unconfirmedAddress;
    private byte[] secret;
    private long secretEndOfLife;

    private static final Logger log = Logging.getLogger ("network");

    public InetAddress get() {
        InetAddress address = confirmedAddress;
        if (address == null)
        	address = localAddress;
        return address;
    }

    private static byte[] getNextSecret() {
        if (prng == null) {
            prng = new Random(System.currentTimeMillis());
        }
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
        buf.putLong(prng.nextLong());
        return buf.array();
    }

    private static void sendSecret(InetAddress address, byte[] secret) throws IOException {
        int port = Modules.get(Universe.class).getPort();
        DatagramPacket packet = new DatagramPacket(secret, secret.length, address, port);
        DatagramSocket socket = new DatagramSocket(null);
        socket.send(packet);
        socket.close();
    }

    static boolean isPublicUnicastInetAddress(InetAddress address) {
        return ! (address.isSiteLocalAddress() || address.isLinkLocalAddress() ||
                  address.isLoopbackAddress() || address.isMulticastAddress());
    }

    /**
     * Sends a challenge to the given address if necessary.
     *
     * The caller will receive a special UDP, which should be passed to the endValidation() methods.
     *
     * @param address untrusted address to validate
     * @see #endValidation(DatagramPacket)
     */
    void startValidation(InetAddress address) throws IOException {
        byte[] data;

        // update state in a thread-safe manner
        synchronized (this) {
            if (address == null || address.equals(confirmedAddress)) {
                confirmedAddress = address;
                return;
            }
            if (secretEndOfLife > System.currentTimeMillis()) {
                return;
            }

            unconfirmedAddress = address;
            data = secret = getNextSecret();

            // secret is valid for a minute - plenty of time to validate the address
            // in the mean time we do not trigger of new validation - it could act as an attack vector.
            secretEndOfLife = System.currentTimeMillis() + 60000;
        }

        log.info("validating untrusted public address: " + address);
        sendSecret(address, data);
    }

    /**
     * The caller needs to filter all packets with this method to catch validation UDP frames.
     *
     * @param packet packet previously sent by start validation.
     * @return true when packet was part of the validation process(and can be ignored by the caller) false otherwise.
     */
    boolean endValidation(DatagramPacket packet) {
        // quick return - in case this is the wrong packet
        if (packet == null || packet.getLength() > Long.BYTES) {
            return false;
        }

        byte[] data = packet.getData();
        if (!Arrays.equals(data, secret)) {
            return false;
        }
        log.info("public address is confirmed valid: " + unconfirmedAddress);

        // update state in a thread-safe manner
        synchronized (this) {
            confirmedAddress = unconfirmedAddress;
        }

        // tell the caller that this packet should be ignored.
        return true;
    }
}
