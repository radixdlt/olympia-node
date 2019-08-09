package org.radix.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.network.PublicInetAddress;

import com.radixdlt.utils.Longs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PublicInetAddressTest {

    private PublicInetAddress dut;
    private AtomicLong clock;

    static class FakeDatagramSocket extends DatagramSocket {
    	FakeDatagramSocket(SocketAddress address) throws SocketException {
    		super((SocketAddress) null); // ensure created unbound
    	}

    	@Override
		public void send(DatagramPacket p) throws IOException {
    		// Do nothing
    	}

    	@Override
		public void close() {
    		// Do nothing
    	}
    }

    @Before
    public void setUp() {
    	clock = new AtomicLong(0);
        dut = new PublicInetAddress("10.10.10.10", 30000, clock::get, FakeDatagramSocket::new);
    }

    @Test
    public void testGet() {
        assertNotNull(dut.get());
    }

    @Test
    public void testIsPublicUnicastInetAddress() throws UnknownHostException {
    	assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("172.31.0.1")));  // Site-local
    	assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.168.1.1"))); // Site-local
    	assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("10.10.10.10"))); // Site-local
    	assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("169.254.0.0"))); // Link-local
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("127.0.0.1")));   // Localhost
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("224.0.0.101"))); // Multicast
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("::1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.169.1.1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("2260::1")));
    }

    @Test
    public void testStartValidation() throws Exception {
    	// Reset
        dut.startValidation(null);

        // single validation until confirmed or time has elapsed
        dut.startValidation(InetAddress.getByName("127.0.0.1"));
        long expectedTime = (long) Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut);
        long expectedSecret = (long) Whitebox.getField(PublicInetAddress.class, "secret").get(dut);

        // make sure our clock returns something new.
        clock.incrementAndGet();

        // make sure time changed
        assertNotEquals(expectedTime, clock.get());

        // try to trigger validation again with the same address
        dut.startValidation(InetAddress.getByName("127.0.0.1"));

        // try to trigger validation again with a different address
        dut.startValidation(InetAddress.getByName("172.31.0.1"));

        // make sure secretEndOfLife did not change since the first valid invocation
        assertEquals(expectedTime, Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut));
        // make sure secret did not change since the first valid invocation
        assertEquals(expectedSecret, Whitebox.getField(PublicInetAddress.class, "secret").get(dut));

        // make sure unconfirmedAddress did not change since the first valid invocation
        assertEquals(InetAddress.getByName("127.0.0.1"), Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").get(dut));

        // Timeout the secret
        clock.addAndGet(PublicInetAddress.SECRET_LIFETIME_MS);

        // trigger validation again with a different address
        dut.startValidation(InetAddress.getByName("172.31.0.1"));

        // make sure secretEndOfLife changed
        assertNotEquals(expectedTime, Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut));
        // make sure secret changed
        assertNotEquals(expectedSecret, Whitebox.getField(PublicInetAddress.class, "secret").get(dut));
        // make sure unconfirmedAddress changed
        assertEquals(InetAddress.getByName("172.31.0.1"), Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").get(dut));

        // get should return local address (10.10.10.10 from constructor)
        assertEquals(InetAddress.getByName("10.10.10.10"), dut.get());
    }

    @Test
    public void testEndValidation() throws IllegalAccessException, IOException {
        long secret = -1L;
        Whitebox.getField(PublicInetAddress.class, "secret").set(dut, secret);
        Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").set(dut, InetAddress.getByName("1.1.1.1"));

        // Check initial conditions
        assertNotEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));

        assertFalse(dut.endValidation(null, 0, 0));
        assertFalse(dut.endValidation(new byte[] {1, 2, 3}, 0, 3));
        assertFalse(dut.endValidation(Longs.toByteArray(0L), 0, Long.BYTES));
        assertTrue(dut.endValidation(Longs.toByteArray(secret), 0, Long.BYTES));
        // make sure that confirmedAddress got updated
        assertEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));

        // get should return confirmed address
        assertEquals(InetAddress.getByName("1.1.1.1"), dut.get());
        assertEquals("1.1.1.1", dut.toString());

        // no new secret now if we start again with the same address
        long oldSecret = Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut);
        dut.startValidation(InetAddress.getByName("1.1.1.1"));
        assertEquals(oldSecret, Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut));

        // ... but should get a new secret if we start again with a new host
        dut.startValidation(InetAddress.getByName("2.2.2.2"));
        assertNotEquals(oldSecret, Whitebox.getField(PublicInetAddress.class, "secret").getLong(dut));
    }
}
