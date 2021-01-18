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

import java.net.InetAddress;
import java.net.InetSocketAddress;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.messaging.InboundMessage;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;

public class UDPNettyMessageHandlerTest {

	private SystemCounters counters;

	@Before
	public void setup() {
		this.counters = mock(SystemCounters.class);
	}

	@Test
	public void testChannelRead0() throws Exception {
		NatHandler inetAddress = mock(NatHandler.class);
		when(inetAddress.endInboundValidation(any())).thenReturn(false);
		UDPNettyMessageHandler mh = new UDPNettyMessageHandler(counters, 255, inetAddress);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		ByteBuf buf = Unpooled.copiedBuffer(data);
		InetSocketAddress recipient = mock(InetSocketAddress.class);
		InetAddress senderAddress = mock(InetAddress.class);
		when(senderAddress.getHostAddress()).thenReturn("127.0.0.1");
		InetSocketAddress sender = mock(InetSocketAddress.class);
		when(sender.getAddress()).thenReturn(senderAddress);
		DatagramPacket dp = new DatagramPacket(buf, recipient, sender);
		mh.channelRead0(ctx, dp);

		testSubscriber.awaitCount(1);
		testSubscriber.assertValueCount(1);
		testSubscriber.assertNoErrors();
	}

	@Test
	public void testChannelRead0NotConnected() throws Exception {
		NatHandler inetAddress = mock(NatHandler.class);
		when(inetAddress.endInboundValidation(any())).thenReturn(true);
		UDPNettyMessageHandler mh = new UDPNettyMessageHandler(counters, 255, inetAddress);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		ByteBuf buf = Unpooled.copiedBuffer(data);
		InetSocketAddress recipient = mock(InetSocketAddress.class);
		InetSocketAddress sender = mock(InetSocketAddress.class);
		DatagramPacket dp = new DatagramPacket(buf, recipient, sender);
		mh.channelRead0(ctx, dp);

		testSubscriber.assertNoValues();
	}

	@Test
	public void testExceptionCaughtChannelHandlerContextThrowable() throws Exception {
		NatHandler inetAddress = mock(NatHandler.class);
		UDPNettyMessageHandler mh = new UDPNettyMessageHandler(counters, 255, inetAddress);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		ChannelHandlerContext ctx = createContext("127.0.0.1", 4321);

		mh.exceptionCaught(ctx, new Exception("dummy exception"));

		testSubscriber.assertNoValues();
	}

	@Test
	public void testBufferOverflow() throws Exception {
		int bufferSize = 5;
		int overflowSize = 4;

		NatHandler inetAddress = mock(NatHandler.class);
		when(inetAddress.endInboundValidation(any())).thenReturn(false);
		UDPNettyMessageHandler mh = new UDPNettyMessageHandler(counters, bufferSize, inetAddress);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test(0);

		ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		for (int i = 0; i < bufferSize + overflowSize; i++) {
			InetSocketAddress recipient = mock(InetSocketAddress.class);
			InetAddress senderAddress = mock(InetAddress.class);
			when(senderAddress.getHostAddress()).thenReturn("127.0.0.1");
			InetSocketAddress sender = mock(InetSocketAddress.class);
			when(sender.getAddress()).thenReturn(senderAddress);
			DatagramPacket dp = new DatagramPacket(Unpooled.copiedBuffer(data), recipient, sender);
			mh.channelRead0(ctx, dp);
		}

		verify(counters, times(overflowSize)).increment(CounterType.NETWORKING_UDP_DROPPED_MESSAGES);
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
