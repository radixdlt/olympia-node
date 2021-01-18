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

package com.radixdlt.network.transport.udp;

import java.io.IOException;

import com.radixdlt.counters.SystemCounters;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NettyUDPTransportImplTest {

	private SystemCounters counters;

	@Before
	public void setup() {
		this.counters = mock(SystemCounters.class);
	}

	@Test
	public void testConstruction() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_HOST, "127.0.0.1",
			UDPConstants.METADATA_PORT, "10000"
		);
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			assertEquals("127.0.0.1", testInstance.localMetadata().get(UDPConstants.METADATA_HOST));
			assertEquals("10000", testInstance.localMetadata().get(UDPConstants.METADATA_PORT));
		}
	}

	@Test
	public void testConstructionNoHost() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_PORT, "20000"
		);
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			assertEquals(NettyUDPTransportImpl.DEFAULT_HOST, testInstance.localMetadata().get(UDPConstants.METADATA_HOST));
			assertEquals("20000", testInstance.localMetadata().get(UDPConstants.METADATA_PORT));
		}
	}

	@Test
	public void testConstructionNoPort() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
				UDPConstants.METADATA_HOST, "127.0.0.2"
		);
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance =
				 new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			assertEquals("127.0.0.2", testInstance.localMetadata().get(UDPConstants.METADATA_HOST));
			assertEquals(
				String.valueOf(NettyUDPTransportImpl.DEFAULT_PORT),
				testInstance.localMetadata().get(UDPConstants.METADATA_PORT)
			);
		}
	}

	@Test
	public void testName() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance =
			 	new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			assertEquals(UDPConstants.NAME, testInstance.name());
		}
	}

	@Test
	public void testControl() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);

		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			// Always null until started
			assertNull(testInstance.control());
		}
	}

	@Test
	public void testCanHandle() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			byte[] max = new byte[UDPConstants.MAX_PACKET_LENGTH];
			byte[] tooBig = new byte[UDPConstants.MAX_PACKET_LENGTH + 1];
			assertTrue(testInstance.canHandle(null));
			assertTrue(testInstance.canHandle(max));
			assertFalse(testInstance.canHandle(tooBig));
		}
	}

	@Test
	public void testPriority() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);
		when(config.priority(anyInt())).thenReturn(1234);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			assertEquals(1234, testInstance.priority());
		}
	}

	@Test
	public void sensibleToString() throws IOException {
		UDPConfiguration config = mock(UDPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyUDPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyUDPTransportImpl.DEFAULT_PORT);
		when(config.priority(anyInt())).thenReturn(1234);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		UDPTransportControlFactory controlFactory = mock(UDPTransportControlFactory.class);
		UDPTransportOutboundConnectionFactory connectionFactory = mock(UDPTransportOutboundConnectionFactory.class);
		NatHandler natHandler = mock(NatHandler.class);

		try (var testInstance = new NettyUDPTransportImpl(counters, config, localMetadata, controlFactory, connectionFactory, natHandler)) {
			String s = testInstance.toString();
			assertThat(s).contains(NettyUDPTransportImpl.class.getSimpleName());
			assertThat(s).contains(NettyUDPTransportImpl.DEFAULT_HOST);
			assertThat(s).contains(String.valueOf(NettyUDPTransportImpl.DEFAULT_PORT));
		}
	}
}
