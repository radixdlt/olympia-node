package org.radix.network;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.radix.network.PublicInetAddress;

import com.radixdlt.utils.Longs;

import static org.powermock.api.mockito.PowerMockito.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PublicInetAddress.class})
public class PublicInetAddressTest {
    private PublicInetAddress dut;
    private AtomicLong clock;

    @Before
    public void setUp() {
    	clock = new AtomicLong(0);
        dut = new PublicInetAddress(null, 30000, clock::get);
    }

    @Test
    public void testGet() {
        assertNotNull(dut.get());
    }

    @Test
    public void testIsPublicUnicastInetAddress() throws UnknownHostException {
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("172.31.0.1")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.168.10.10")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("10.10.10.10")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("224.0.0.101")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("::1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.169.1.1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("2260::1")));
    }

    @Test
    public void testStartValidation() throws Exception {
    	spy(PublicInetAddress.class);
    	doNothing().when(PublicInetAddress.class, method(PublicInetAddress.class, "sendSecret"));

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


    }

    @Test
    public void testEndValidation() throws IllegalAccessException, UnknownHostException {
        long secret = -1L;
        Whitebox.getField(PublicInetAddress.class, "secret").set(dut, secret);
        Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").set(dut, InetAddress.getByName("1.1.1.1"));

        // Check initial conditions
        assertNotEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));

        assertFalse(dut.endValidation(null));
        assertFalse(dut.endValidation(new DatagramPacket(new byte[] {1, 2, 3}, 3)));
        assertTrue(dut.endValidation(new DatagramPacket(Longs.toByteArray(secret), Long.BYTES)));
        // make sure that confirmedAddress got updated
        assertEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));
    }
}
