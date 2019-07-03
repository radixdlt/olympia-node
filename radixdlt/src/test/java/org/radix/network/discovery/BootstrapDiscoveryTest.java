package org.radix.network.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.Set;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.security.*", "javax.security.*", "org.bouncycastle.*"})
@PrepareForTest({BootstrapDiscovery.class, Modules.class, URL.class, URLConnection.class, RuntimeProperties.class, Universe.class, Thread.class})
public class BootstrapDiscoveryTest {
    // Mocks
    private RuntimeProperties config;
    private URL url;
    private URLConnection conn;

    // Methods
    private Method toHost;
    private Method getNextNode;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        // create stubs
        spy(Modules.class);
        spy(BootstrapDiscovery.class);
        spy(Thread.class);
        config = mock(RuntimeProperties.class);
        Universe universe = mock(Universe.class);
        url = mock(URL.class);
        conn = mock(URLConnection.class);

        // Expose private methods
        toHost = Whitebox.getMethod(BootstrapDiscovery.class, "toHost", new byte[0].getClass(), int.class);
        getNextNode = Whitebox.getMethod(BootstrapDiscovery.class, "getNextNode", URL.class);

        // initialize stubs
        doNothing().when(BootstrapDiscovery.class, "testConnection", anyString(), anyInt(), anyInt());
        doReturn(config).when(Modules.class, "get", RuntimeProperties.class);
        doReturn(30).when(config).get("network.discovery.connection.retries", 30);
        doReturn(1).when(config).get("network.discovery.connection.cooldown", 1);
        doReturn(60000).when(config).get("network.discovery.connection.timeout", 60000);
        doReturn(60000).when(config).get("network.discovery.read.timeout", 60000);
        doReturn(0).when(config).get("network.discovery.allow_tls_bypass", 0);
        doReturn(8).when(config).get("network.connections.in", 8);
        doReturn(8).when(config).get("network.connections.out", 8);
        doReturn(universe).when(Modules.class, "get", Universe.class);

        when(config.get(eq("network.discovery.connection.retries"), any())).thenReturn(1);
        when(config.get(eq("network.discovery.connection.cooldown"), any())).thenReturn(1);
        when(config.get(eq("network.connections.in"), any())).thenReturn(8);
        when(config.get(eq("network.connections.out"), any())).thenReturn(8);

        when(config.get(eq("network.discovery.connection.timeout"), any())).thenReturn(60000);
        when(config.get(eq("network.discovery.read.timeout"), any())).thenReturn(60000);
        when(config.get(eq("network.discovery.allow_tls_bypass"), any())).thenReturn(0);

        when(universe.getPort()).thenReturn(30000);

        when(url.openConnection()).thenReturn(conn);
    }

	@Test
    public void testToHost_Empty() throws Exception
    {
        assertEquals("",  toHost.invoke(null, new byte[0], 0));
    }

	@Test
    public void testToHost() throws Exception
    {
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
            byte[] buf = new byte[] {(byte) b};
            if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.".indexOf(0xff & b) != -1) {
                assertEquals(new String(buf, "US-ASCII"), toHost.invoke(null, buf, buf.length));
            } else {
                assertEquals(null,  toHost.invoke(null, buf, buf.length));
            }
        }
    }

    @Test
	public void testGetNextNode() throws Exception
	{
        String expected = "1.1.1.1";
        doReturn(expected.length()).when(conn).getContentLength();
        doReturn(new ByteArrayInputStream(expected.getBytes("US-ASCII"))).when(conn).getInputStream();
        assertEquals(expected,  getNextNode.invoke(null, url));
    }

    @Test
	public void testGetNextNode_RuntimeException() throws Exception
	{
        doThrow(new RuntimeException()).when(conn).connect();
        assertEquals(null,  getNextNode.invoke(null, url));
    }

    @Test
	public void testGetNextNode_InterruptedException() throws Exception
	{
        doThrow(new InterruptedException()).when(Thread.class);
        Thread.sleep(anyLong());
        assertEquals(null,  getNextNode.invoke(null, url));
    }

    @Test
	public void testConstructor_Seeds() throws Exception
	{
        doReturn("").when(config).get("network.discovery.urls", "");
        doReturn("1.1.1.1").when(config).get("network.seeds", "");
        doReturn(Integer.valueOf(8)).when(config).get("network.connections.in", Integer.valueOf(8));
        doReturn(Integer.valueOf(8)).when(config).get("network.connections.out", Integer.valueOf(8));
        BootstrapDiscovery testSubject = Whitebox.invokeConstructor(BootstrapDiscovery.class);
        Set<?> hosts = Whitebox.getInternalState(testSubject, "hosts");
        assertEquals(1, hosts.size());
    }

    @Test
	public void testConstructor_NeedHTTPS() throws Exception
	{
        thrown.expect(IllegalStateException.class);
        doReturn("http://example.com").when(config).get("network.discovery.urls", "");
        Whitebox.invokeConstructor(BootstrapDiscovery.class);
    }

    @Test
	public void testConstructor() throws Exception
	{
        doReturn("https://example.com").when(config).get("network.discovery.urls", "");
        doReturn("1.1.1.1.").when(BootstrapDiscovery.class, "getNextNode", any());
        doReturn("2.2.2.2").when(config).get("network.seeds", "");
        BootstrapDiscovery testSubject = Whitebox.invokeConstructor(BootstrapDiscovery.class);
        Set<?> hosts = Whitebox.getInternalState(testSubject, "hosts");
        assertEquals(2, hosts.size());
    }
}
