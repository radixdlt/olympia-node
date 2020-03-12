/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network.discovery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"java.security.*", "javax.security.*", "org.bouncycastle.*"})
@PrepareForTest({BootstrapDiscovery.class, URL.class, URLConnection.class, RuntimeProperties.class, Universe.class, Thread.class})
public class BootstrapDiscoveryTest {
    // Mocks
    private RuntimeProperties config;
    private URL url;
    private URLConnection conn;
    private Universe universe;

    // Methods
    private Method toHost;
    private Method getNextNode;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws IOException {
        // create stubs
        spy(BootstrapDiscovery.class);
        spy(Thread.class);
        config = defaultProperties();
        universe = mock(Universe.class);
        url = mock(URL.class);
        conn = mock(URLConnection.class);

        // Expose private methods
        toHost = Whitebox.getMethod(BootstrapDiscovery.class, "toHost", new byte[0].getClass(), int.class);
        getNextNode = Whitebox.getMethod(BootstrapDiscovery.class, "getNextNode", URL.class);

        // initialize stubs
        doReturn(30).when(config).get("network.discovery.connection.retries", 30);
        doReturn(1).when(config).get("network.discovery.connection.cooldown", 1);
        doReturn(60000).when(config).get("network.discovery.connection.timeout", 60000);
        doReturn(60000).when(config).get("network.discovery.read.timeout", 60000);
        doReturn(0).when(config).get("network.discovery.allow_tls_bypass", 0);
        doReturn(8).when(config).get("network.connections.in", 8);
        doReturn(8).when(config).get("network.connections.out", 8);
        doReturn(8192).when(config).get("messaging.inbound.queue_max", 8192);
        doReturn(1 << 18).when(config).get("network.udp.buffer", 1 << 18);

        when(config.get(eq("network.discovery.connection.retries"), anyInt())).thenReturn(1);
        when(config.get(eq("network.discovery.connection.cooldown"), anyInt())).thenReturn(1);
        when(config.get(eq("network.connections.in"), anyInt())).thenReturn(8);
        when(config.get(eq("network.connections.out"), anyInt())).thenReturn(8);

        when(config.get(eq("network.discovery.connection.timeout"), anyInt())).thenReturn(60000);
        when(config.get(eq("network.discovery.read.timeout"), anyInt())).thenReturn(60000);
        when(config.get(eq("network.discovery.allow_tls_bypass"), anyInt())).thenReturn(0);

        when(universe.getPort()).thenReturn(30000);

        when(url.openConnection()).thenReturn(conn);
    }

    @After
    public void tearDown() {
        // Make sure throwing interrupted exception doesn't affect other tests
        Thread.interrupted();
    }

	@Test
    public void testToHost_Empty() throws ReflectiveOperationException
    {
        assertEquals("",  toHost.invoke(null, new byte[0], 0));
    }

	@Test
    public void testToHost() throws ReflectiveOperationException
    {
        for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
            byte[] buf = new byte[] {(byte) b};
            if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-.".indexOf(0xff & b) != -1) {
                assertEquals(new String(buf, StandardCharsets.US_ASCII), toHost.invoke(null, buf, buf.length));
            } else {
                assertEquals(null,  toHost.invoke(null, buf, buf.length));
            }
        }
    }

    @Test
	public void testGetNextNode() throws IOException, ReflectiveOperationException
	{
        String expected = "1.1.1.1";
        doReturn(expected.length()).when(conn).getContentLength();
        doReturn(new ByteArrayInputStream(expected.getBytes(StandardCharsets.US_ASCII))).when(conn).getInputStream();
        assertEquals(expected,  getNextNode.invoke(new BootstrapDiscovery(config, universe), url));
    }

    @Test
	public void testGetNextNode_RuntimeException() throws IOException, ReflectiveOperationException
	{
        doThrow(new RuntimeException()).when(conn).connect();
        assertEquals(null,  getNextNode.invoke(new BootstrapDiscovery(config, universe), url));
    }

    @Test
	public void testGetNextNode_InterruptedException() throws ReflectiveOperationException, InterruptedException
	{
        doThrow(new InterruptedException()).when(Thread.class);
        Thread.sleep(anyLong());
        assertEquals(null,  getNextNode.invoke(new BootstrapDiscovery(config, universe), url));
    }

    @Test
	public void testConstructor_Seeds() throws Exception
	{
        doReturn("").when(config).get("network.discovery.urls", "");
        doReturn("1.1.1.1").when(config).get("network.seeds", "");
        doReturn(8).when(config).get(eq("network.connections.in"), anyInt());
        doReturn(8).when(config).get(eq("network.connections.out"), anyInt());
        doReturn(8000).when(config).get(eq("messaging.inbound.queue_max"), anyInt());
        doReturn(8000).when(config).get(eq("messaging.outbound.queue_max"), anyInt());
        doReturn(30).when(config).get(eq("messaging.time_to_live"), anyInt());
        BootstrapDiscovery testSubject = Whitebox.invokeConstructor(BootstrapDiscovery.class, config, universe);
        Set<?> hosts = Whitebox.getInternalState(testSubject, "hosts");
        assertEquals(1, hosts.size());
    }

    @Test
	public void testConstructor_NeedHTTPS() throws Exception
	{
        thrown.expect(IllegalStateException.class);
        doReturn("http://example.com").when(config).get("network.discovery.urls", "");
        Whitebox.invokeConstructor(BootstrapDiscovery.class, config, universe);
    }

    private static RuntimeProperties defaultProperties() {
        RuntimeProperties properties = mock(RuntimeProperties.class);
        doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());
        return properties;
    }
}
