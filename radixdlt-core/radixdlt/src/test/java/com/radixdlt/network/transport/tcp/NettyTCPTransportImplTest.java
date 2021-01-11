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

package com.radixdlt.network.transport.tcp;

import java.io.IOException;

import org.junit.Test;

import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportMetadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NettyTCPTransportImplTest {

	@Test
	public void testConstruction() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "127.0.0.1",
			TCPConstants.METADATA_PORT, "10000"
		);
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertEquals("127.0.0.1", testInstance.localMetadata().get(TCPConstants.METADATA_HOST));
			assertEquals("10000", testInstance.localMetadata().get(TCPConstants.METADATA_PORT));
		}
	}

	@Test
	public void testConstructionNoHost() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_PORT, "20000"
		);
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertEquals(NettyTCPTransportImpl.DEFAULT_HOST, testInstance.localMetadata().get(TCPConstants.METADATA_HOST));
			assertEquals("20000", testInstance.localMetadata().get(TCPConstants.METADATA_PORT));
		}
	}

	@Test
	public void testConstructionNoPort() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.of(
				TCPConstants.METADATA_HOST, "127.0.0.2"
		);
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertEquals("127.0.0.2", testInstance.localMetadata().get(TCPConstants.METADATA_HOST));
			assertEquals(String.valueOf(NettyTCPTransportImpl.DEFAULT_PORT), testInstance.localMetadata().get(TCPConstants.METADATA_PORT));
		}
	}

	@Test
	public void testName() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertEquals(TCPConstants.NAME, testInstance.name());
		}
	}

	@Test
	public void testControl() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		// Resource leak not an issue for mocks
		@SuppressWarnings("resource")
		TCPTransportControl control = mock(TCPTransportControl.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);
		when(controlFactory.create(any(), any(), any())).thenReturn(control);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertSame(control, testInstance.control());
		}
	}

	@Test
	public void testCanHandle() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		// Resource leak not an issue for mocks
		@SuppressWarnings("resource")
		TCPTransportControl control = mock(TCPTransportControl.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);
		when(controlFactory.create(any(), any(), any())).thenReturn(control);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			byte[] max = new byte[TCPConstants.MAX_PACKET_LENGTH];
			byte[] tooBig = new byte[TCPConstants.MAX_PACKET_LENGTH + 1];
			assertTrue(testInstance.canHandle(null));
			assertTrue(testInstance.canHandle(max));
			assertFalse(testInstance.canHandle(tooBig));
		}
	}

	@Test
	public void testPriority() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		when(config.priority(anyInt())).thenReturn(1234);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			assertEquals(1234, testInstance.priority());
		}
	}

	@Test
	public void sensibleToString() throws IOException {
		TCPConfiguration config = mock(TCPConfiguration.class);
		when(config.networkAddress(any())).thenReturn(NettyTCPTransportImpl.DEFAULT_HOST);
		when(config.networkPort(anyInt())).thenReturn(NettyTCPTransportImpl.DEFAULT_PORT);
		TransportMetadata localMetadata = StaticTransportMetadata.empty();
		TCPTransportOutboundConnectionFactory outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		TCPTransportControlFactory controlFactory = mock(TCPTransportControlFactory.class);

		try (NettyTCPTransportImpl testInstance = new NettyTCPTransportImpl(config, localMetadata, outboundFactory, controlFactory)) {
			String s = testInstance.toString();
			assertThat(s).contains(NettyTCPTransportImpl.class.getSimpleName());
			assertThat(s).contains(NettyTCPTransportImpl.DEFAULT_HOST);
			assertThat(s).contains(String.valueOf(NettyTCPTransportImpl.DEFAULT_PORT));
		}
	}
}
