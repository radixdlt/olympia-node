package org.radix.network;

import java.net.DatagramPacket;
import java.net.InetAddress;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.radix.network.PublicInetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.spy;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PublicInetAddress.class})
public class PublicInetAddressTest {
    private PublicInetAddress dut;

    @Before
    public void setUp() {
        dut = new PublicInetAddress();
    }

    @Test
    public void testGet()
    {
        assertNotNull(dut.get());
    }

    @Test
    public void testGetNextSecret() throws Exception
    {
        // just check for exceptions
        assertNotNull(Whitebox.getMethod(PublicInetAddress.class, "getNextSecret").invoke(null));
    }

    @Test
    public void testIsPublicUnicastInetAddress() throws Exception
    {
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("172.31.0.1")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("192.168.10.10")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("10.10.10.10")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("224.0.0.101")));
        assertFalse(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("::1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(PublicInetAddress.isPublicUnicastInetAddress(InetAddress.getByName("1.1.1.1")));
    }

    @Test
    public void testStartValidation() throws Exception
    {
        spy(PublicInetAddress.class);
        doNothing().when(PublicInetAddress.class, "sendSecret", any(InetAddress.class), any(byte[].class));

        dut.startValidation(null);

        // single validation until confirmed or time has elapsed
        dut.startValidation(InetAddress.getByName("127.0.0.1"));
        long expectedTime = (long) Whitebox.getField(PublicInetAddress.class, "secretEndOfLife").get(dut);
        byte[] expectedSecret = (byte[]) Whitebox.getField(PublicInetAddress.class, "secret").get(dut);
        assertNotNull(expectedSecret);

        // make sure System.currentTimeMillis() returns something new.
        Thread.sleep(100);
        assertNotEquals(expectedTime, System.currentTimeMillis());
        // make sure secret did change

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
    }

    @Test
    public void testEndValidation() throws Exception
    {
        byte[] secret = new byte[] { -1, -1, -1, -1, -1, -1, -1, -1 };
        Whitebox.getField(PublicInetAddress.class, "secret").set(dut, secret);
        Whitebox.getField(PublicInetAddress.class, "unconfirmedAddress").set(dut,InetAddress.getByName("1.1.1.1"));
        assertFalse(dut.endValidation(null));
        assertFalse(dut.endValidation(new DatagramPacket(new byte[] {1, 2, 3}, 3)));
        assertTrue(dut.endValidation(new DatagramPacket(secret, secret.length)));
        // make sure that confirmedAddress got updated
        assertEquals(InetAddress.getByName("1.1.1.1"), Whitebox.getField(PublicInetAddress.class, "confirmedAddress").get(dut));
    }
}
