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

import org.junit.Before;
import org.junit.Test;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.network.transport.tcp.TCPTransportControlImpl.TCPConnectionHandlerChannelInbound;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class TCPTransportControlImplTest {
	private TCPConfiguration config;
	private NettyTCPTransport transport;
	private TCPTransportOutboundConnectionFactory outboundFactory;
	private TransportOutboundConnection transportOutboundConnection;
	private SystemCounters counters;

	@Before
	public void setUp() {
		config = new TCPConfiguration() {
			@Override
			public int networkPort(int defaultValue) {
				return 0;
			}

			@Override
			public String networkAddress(String defaultValue) {
				return "127.0.0.1";
			}

			@Override
			public int maxChannelCount(int defaultValue) {
				return 1;
			}

			@Override
			public int priority(int defaultValue) {
				return 0;
			}

			@Override
			public boolean debugData(boolean defaultValue) {
				return false;
			}
		};

		transportOutboundConnection = mock(TransportOutboundConnection.class);

		outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
		when(outboundFactory.create(any(), any())).thenReturn(transportOutboundConnection);

		Channel ch = mock(Channel.class);
		ChannelFuture cf = mock(ChannelFuture.class);
		when(cf.addListener(any())).thenAnswer(a -> {
			GenericFutureListener<Future<Void>> listener = a.getArgument(0);
			listener.operationComplete(cf);
			return cf;
		});
		when(cf.channel()).thenReturn(ch);

		transport = mock(NettyTCPTransport.class);
		when(transport.createChannel(any(), anyInt())).thenReturn(cf);

		this.counters = mock(SystemCounters.class);
	}

	@Test
	public void open() throws ExecutionException, InterruptedException, IOException {
		try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(config, outboundFactory, transport, counters)) {
			TransportMetadata metadata = StaticTransportMetadata.of(
				TCPConstants.METADATA_HOST, "localhost",
				TCPConstants.METADATA_PORT, "443"
			);
			CompletableFuture<TransportOutboundConnection> result = tcpTransportControl.open(metadata);
			assertThat(result.get()).isEqualTo(transportOutboundConnection);
		}
	}

	@Test
	public void channelActive() throws Exception {
		try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(config, outboundFactory, transport, counters)) {
			ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
			TCPConnectionHandlerChannelInbound handler = (TCPConnectionHandlerChannelInbound) tcpTransportControl.handler();
			handler.channelActive(ctx);
			assertEquals(1, handler.channelMapSize());
		}
	}

	@Test
	public void tooManyChannels() throws Exception {
		try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(config, outboundFactory, transport, counters)) {
			ChannelHandlerContext ctx1 = createContext("127.0.0.1", 1234);
			ChannelHandlerContext ctx2 = createContext("127.0.0.2", 4321);
			TCPConnectionHandlerChannelInbound handler = (TCPConnectionHandlerChannelInbound) tcpTransportControl.handler();
			handler.channelActive(ctx1);
			handler.channelActive(ctx2);
			assertEquals(1, handler.channelMapSize());
			assertEquals(1, handler.droppedChannels());
		}
	}

	@Test
	public void channelInactive() throws Exception {
		try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(config, outboundFactory, transport, counters)) {
			ChannelHandlerContext ctx1 = createContext("127.0.0.1", 1234);
			TCPConnectionHandlerChannelInbound handler = (TCPConnectionHandlerChannelInbound) tcpTransportControl.handler();
			handler.channelActive(ctx1);
			assertEquals(1, handler.channelMapSize());
			handler.channelInactive(ctx1);
			assertEquals(0, handler.channelMapSize());
		}
	}

	@Test
	public void findOrCreateActiveChannelNew() throws Exception {
		try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(config, outboundFactory, transport, counters)) {
			TransportMetadata metadata = StaticTransportMetadata.of(
				TCPConstants.METADATA_HOST, "127.0.0.1",
				TCPConstants.METADATA_PORT, "1234"
			);
			TCPConnectionHandlerChannelInbound handler = (TCPConnectionHandlerChannelInbound) tcpTransportControl.handler();

			CompletableFuture<TransportOutboundConnection> cf = handler.findOrCreateActiveChannel(metadata, transport, outboundFactory);
			assertTrue(cf.isDone());
			assertEquals(1, handler.pendingMapSize());
			assertEquals(0, handler.channelMapSize());

			ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
			handler.channelActive(ctx);
			assertEquals(0, handler.pendingMapSize());
			assertEquals(1, handler.channelMapSize());
		}
	}

	ChannelHandlerContext createContext(String host, int port) {
		InetSocketAddress isa = new InetSocketAddress(host, port);
		SocketChannel sch = mock(SocketChannel.class);
		when(sch.localAddress()).thenReturn(isa);
		when(sch.remoteAddress()).thenReturn(isa);
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		when(ctx.channel()).thenReturn(sch);
		return ctx;
	}
}