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

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.messaging.InboundMessage;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

public class TCPNettyMessageHandlerTest {

	private SystemCounters counters;

	@Before
	public void setup() {
		this.counters = mock(SystemCounters.class);
	}

	@Test
	public void testChannelRead0() {
		TCPNettyMessageHandler mh = new TCPNettyMessageHandler(counters, 255);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		ByteBuf buf = Unpooled.copiedBuffer(data);
		mh.channelRead0(ctx, buf);

		testSubscriber.awaitCount(1);
		testSubscriber.assertValueCount(1);
		testSubscriber.assertNoErrors();
	}

	@Test
	public void testChannelRead0NotInetSocketAddress() {
		SocketAddress sa = mock(SocketAddress.class);
		Channel sch = mock(Channel.class);
		when(sch.localAddress()).thenReturn(sa);
		when(sch.remoteAddress()).thenReturn(sa);
		ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
		when(ctx.channel()).thenReturn(sch);
		TCPNettyMessageHandler mh = new TCPNettyMessageHandler(counters, 255);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		ByteBuf buf = Unpooled.copiedBuffer(data);
		mh.channelRead0(ctx, buf);

		testSubscriber.assertNoValues();
	}

	@Test
	public void testExceptionCaughtChannelHandlerContextThrowable() {
		TCPNettyMessageHandler mh = new TCPNettyMessageHandler(counters, 255);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test();

		ChannelHandlerContext ctx = createContext("127.0.0.1", 4321);

		mh.exceptionCaught(ctx, new Exception("dummy exception"));

		testSubscriber.assertNoValues();
	}

	@Test
	public void testBufferOverflow() {
		int bufferSize = 5;
		int overflowSize = 4;

		TCPNettyMessageHandler mh = new TCPNettyMessageHandler(counters, bufferSize);
		final TestSubscriber<InboundMessage> testSubscriber = mh.inboundMessageRx().test(0);

		ChannelHandlerContext ctx = createContext("127.0.0.1", 1234);
		byte[] data = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};

		for (int i = 0; i < bufferSize + overflowSize; i++) {
			mh.channelRead0(ctx, Unpooled.copiedBuffer(data));
		}

		verify(counters, times(overflowSize)).increment(CounterType.NETWORKING_TCP_DROPPED_MESSAGES);
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
