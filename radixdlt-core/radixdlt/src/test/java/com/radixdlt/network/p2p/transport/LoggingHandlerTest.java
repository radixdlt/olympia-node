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

package com.radixdlt.network.p2p.transport;

import java.net.SocketAddress;

import com.radixdlt.network.p2p.transport.logging.LogSink;
import com.radixdlt.network.p2p.transport.logging.LoggingHandler;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.StringUtil;

/**
 * Tests for Netty logging handler.
 */
public class LoggingHandlerTest {

	private LogSink logger;
	private LoggingHandler handler;
	private ChannelHandlerContext ctx;
	private Channel channel;
	private boolean isTraceEnabled = true;

	@Before
	public void setUp() {
		this.logger = mock(LogSink.class);
		when(this.logger.isTraceEnabled()).thenReturn(this.isTraceEnabled);
		this.handler = new LoggingHandler(logger, true);

		this.channel = mock(Channel.class);

		this.ctx = mock(ChannelHandlerContext.class);
		doReturn(this.channel).when(this.ctx).channel();
	}

	@Test
	public void testChannelRegisteredChannelHandlerContext() throws Exception {
		this.handler.channelRegistered(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " REGISTERED");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelUnregisteredChannelHandlerContext() throws Exception {
		this.handler.channelUnregistered(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " UNREGISTERED");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelActiveChannelHandlerContext() throws Exception {
		this.handler.channelActive(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " ACTIVE");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelInactiveChannelHandlerContext() throws Exception {
		this.handler.channelInactive(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " INACTIVE");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testExceptionCaughtChannelHandlerContextThrowable() throws Exception {
		Exception ex = new Exception("test exception");
		this.handler.exceptionCaught(ctx, ex);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " EXCEPTION: java.lang.Exception: test exception", ex);
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventTriggeredChannelHandlerContextObject() throws Exception {
		Object randomObject = new Object();
		this.handler.userEventTriggered(ctx, randomObject);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: " + randomObject.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testBindChannelHandlerContextSocketAddressChannelPromise() throws Exception {
		SocketAddress addr = mock(SocketAddress.class);
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.bind(ctx, addr, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " BIND: " + addr.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testConnectChannelHandlerContextSocketAddressSocketAddressChannelPromise() throws Exception {
		SocketAddress addr1 = mock(SocketAddress.class);
		SocketAddress addr2 = mock(SocketAddress.class);
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.connect(ctx, addr1, addr2, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " CONNECT: " + addr1.toString() + ", " + addr2.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testDisconnectChannelHandlerContextChannelPromise() throws Exception {
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.disconnect(ctx, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " DISCONNECT");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testCloseChannelHandlerContextChannelPromise() throws Exception {
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.close(ctx, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " CLOSE");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testDeregisterChannelHandlerContextChannelPromise() throws Exception {
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.deregister(ctx, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " DEREGISTER");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelReadCompleteChannelHandlerContext() throws Exception {
		this.handler.channelReadComplete(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " READ_COMPLETE");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelReadChannelHandlerContextObject() throws Exception {
		Object randomObject = new Object();
		this.handler.channelRead(ctx, randomObject);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " READ: " + randomObject.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testWriteChannelHandlerContextObjectChannelPromise() throws Exception {
		Object randomObject = new Object();
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.write(ctx, randomObject, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " WRITE: " + randomObject.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelWritabilityChangedChannelHandlerContext() throws Exception {
		this.handler.channelWritabilityChanged(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " WRITABILITY_CHANGED");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testFlushChannelHandlerContext() throws Exception {
		this.handler.flush(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " FLUSH");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testConnectChannelHandlerContextSocketAddressNullChannelPromise() throws Exception {
		SocketAddress addr1 = mock(SocketAddress.class);
		ChannelPromise promise = mock(ChannelPromise.class);
		this.handler.connect(ctx, addr1, null, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " CONNECT: " + addr1.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventTriggeredChannelHandlerContextEmptyByteBuf() throws Exception {
		ByteBuf byteBuf = Unpooled.EMPTY_BUFFER;
		this.handler.userEventTriggered(ctx, byteBuf);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: 0B");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventTriggeredChannelHandlerContextByteBuf() throws Exception {
		byte[] bytes = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'
		};
		ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
		this.handler.userEventTriggered(ctx, byteBuf);

	    String newline = StringUtil.NEWLINE;
	    StringBuilder sb = new StringBuilder(this.channel.toString())
	    	.append(" USER_EVENT: ")
	    	.append(bytes.length)
	    	.append('B')
	    	.append(newline);
	    ByteBufUtil.appendPrettyHexDump(sb, byteBuf);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(sb.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventTriggeredChannelHandlerContextEmptyByteBufHolder() throws Exception {
		ByteBuf byteBuf = Unpooled.EMPTY_BUFFER;
		ByteBufHolder byteBufHolder = new DefaultByteBufHolder(byteBuf);
		this.handler.userEventTriggered(ctx, byteBufHolder);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: " + byteBufHolder.toString() + ", 0B");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventTriggeredChannelHandlerContextByteBufHolder() throws Exception {
		byte[] bytes = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'
		};
		ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
		ByteBufHolder byteBufHolder = new DefaultByteBufHolder(byteBuf);
		this.handler.userEventTriggered(ctx, byteBufHolder);

	    String newline = StringUtil.NEWLINE;
	    StringBuilder sb = new StringBuilder(this.channel.toString())
	    	.append(" USER_EVENT: ")
	    	.append(byteBufHolder.toString())
	    	.append(", ")
	    	.append(bytes.length)
	    	.append('B')
	    	.append(newline);
	    ByteBufUtil.appendPrettyHexDump(sb, byteBuf);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(sb.toString());
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelReadCompleteChannelHandlerContextNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		nologHandler.channelReadComplete(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " READ_COMPLETE");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelReadChannelHandlerContextObjectNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		Object randomObject = new Object();
		nologHandler.channelRead(ctx, randomObject);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " READ: Object");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testWriteChannelHandlerContextObjectChannelPromiseNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		Object randomObject = new Object();
		ChannelPromise promise = mock(ChannelPromise.class);
		nologHandler.write(ctx, randomObject, promise);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " WRITE: Object");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testChannelWritabilityChangedChannelHandlerContextNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		nologHandler.channelWritabilityChanged(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " WRITABILITY_CHANGED");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testFlushChannelHandlerContextNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		nologHandler.flush(ctx);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " FLUSH");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventByteBufNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		byte[] bytes = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'
		};
		ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
		nologHandler.userEventTriggered(ctx, byteBuf);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: " + bytes.length + "B");
		verifyNoMoreInteractions(logger);
	}

	@Test
	public void testUserEventByteBufHolderNoDetails() throws Exception {
		LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
		byte[] bytes = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'
		};
		ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
		ByteBufHolder byteBufHolder = new DefaultByteBufHolder(byteBuf);
		nologHandler.userEventTriggered(ctx, byteBufHolder);

		verify(logger, times(1)).isTraceEnabled();
		verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: " + byteBufHolder.toString() + ", " + bytes.length + "B");
		verifyNoMoreInteractions(logger);
	}

}
