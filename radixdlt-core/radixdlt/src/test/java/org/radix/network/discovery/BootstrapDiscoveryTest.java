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
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;

import com.radixdlt.network.transport.tcp.TCPConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import com.google.common.collect.Iterables;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BootstrapDiscoveryTest {
	// Mocks
	private RuntimeProperties config;
	private URL url;
	private URLConnection conn;
	private TCPConfiguration tcpConfiguration;

	@Before
	public void setUp() throws IOException {
		// create stubs
		config = defaultProperties();
		url = mock(URL.class);
		conn = mock(URLConnection.class);
		tcpConfiguration = mock(TCPConfiguration.class);
		when(tcpConfiguration.listenPort(anyInt())).thenReturn(30000);

		// initialize stubs
		doReturn(8192).when(config).get("messaging.inbound.queue_max", 8192);

		when(config.get(eq("network.discovery.connection.retries"), anyInt())).thenReturn(1);
		when(config.get(eq("network.discovery.connection.cooldown"), anyInt())).thenReturn(1);
		when(config.get(eq("network.connections.in"), anyInt())).thenReturn(8);
		when(config.get(eq("network.connections.out"), anyInt())).thenReturn(8);

		when(config.get(eq("network.discovery.connection.timeout"), anyInt())).thenReturn(60000);
		when(config.get(eq("network.discovery.read.timeout"), anyInt())).thenReturn(60000);
		when(config.get(eq("network.discovery.allow_tls_bypass"), anyInt())).thenReturn(0);

		when(url.openConnection()).thenReturn(conn);
	}

	@After
	public void tearDown() {
		// Make sure throwing interrupted exception doesn't affect other tests
		Thread.interrupted();
	}

	@Test
	public void testToHost_Empty() {
		assertEquals("", BootstrapDiscovery.toHost(new byte[0], 0));
	}

	@Test
	public void testToHost() {
		for (int b = Byte.MIN_VALUE; b <= Byte.MAX_VALUE; b++) {
			byte[] buf = new byte[] { (byte) b };

			if ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-.".indexOf(0xff & b) != -1) {
				assertEquals(new String(buf, StandardCharsets.US_ASCII), BootstrapDiscovery.toHost(buf, buf.length));
			} else {
				assertNull(BootstrapDiscovery.toHost(buf, buf.length));
			}
		}
	}

	@Test
	public void testGetNextNode() throws IOException {
		String expected = "1.1.1.1";
		doReturn(expected.length()).when(conn).getContentLength();
		doReturn(new ByteArrayInputStream(expected.getBytes(StandardCharsets.US_ASCII))).when(conn).getInputStream();
		assertEquals(expected, new BootstrapDiscovery(config, tcpConfiguration).getNextNode(url, 1, 1, 1, 1));
	}

	@Test
	public void testGetNextNode_RuntimeException() throws IOException {
		doThrow(new RuntimeException("Test exception")).when(conn).connect();
		assertNull(new BootstrapDiscovery(config, tcpConfiguration).getNextNode(url, 1, 1, 1, 1));
	}

	public void testGetNextNode_InterruptedException() throws InterruptedException {
		doThrow(new InterruptedException()).when(Thread.class);
		Thread.sleep(anyLong());
		assertNull(new BootstrapDiscovery(config, tcpConfiguration).getNextNode(url, 1, 1, 1, 1));
	}

	@Test
	public void testConstructor_Seeds() {
		doReturn("").when(config).get("network.discovery.urls", "");
		doReturn("1.1.1.1").when(config).get("network.seeds", "");
		BootstrapDiscovery testSubject = new BootstrapDiscovery(config, tcpConfiguration);
		Set<?> hosts = Whitebox.getInternalState(testSubject, "hosts");
		assertEquals(1, hosts.size());
	}

	@Test
	public void testConstructor_NeedHTTPS() {
		doReturn("http://example.com").when(config).get("network.discovery.urls", "");
		assertThatThrownBy(() -> new BootstrapDiscovery(config, tcpConfiguration)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void testDiscovery() {
		doReturn("").when(config).get("network.discovery.urls", "");
		doReturn("1.1.1.1").when(config).get("network.seeds", "");
		BootstrapDiscovery discovery = new BootstrapDiscovery(config, tcpConfiguration);

		TransportInfo ti = discovery.toDefaultTransportInfo("1.1.1.1").get();

		Collection<TransportInfo> results = discovery.discoveryHosts();
		assertFalse(results.isEmpty());
		assertEquals(ti, Iterables.getOnlyElement(results));
	}

	private static RuntimeProperties defaultProperties() {
		RuntimeProperties properties = mock(RuntimeProperties.class);
		doAnswer(invocation -> invocation.getArgument(1)).when(properties).get(any(), any());
		return properties;
	}
}
