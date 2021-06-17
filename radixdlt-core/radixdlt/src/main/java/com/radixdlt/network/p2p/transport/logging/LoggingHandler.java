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

package com.radixdlt.network.p2p.transport.logging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;
import java.util.Objects;

import static io.netty.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty.util.internal.StringUtil.NEWLINE;

/**
 * A {@link ChannelHandler} that logs all events using a supplied log sink.
 */
@Sharable
public class LoggingHandler extends ChannelDuplexHandler {

	private final LogSink logger;
	private final boolean includeData;

	/**
	 * Creates a new instance where log messages are sent to the specified
	 * {@link LogSink}.
	 *
	 * @param logger the {@code LogSink} to which all messages will be sent
	 */
	public LoggingHandler(LogSink logger, boolean includeData) {
		this.logger = Objects.requireNonNull(logger);
		this.includeData = includeData;
	}

	@Override
	public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "REGISTERED"));
		}
		ctx.fireChannelRegistered();
	}

	@Override
	public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "UNREGISTERED"));
		}
		ctx.fireChannelUnregistered();
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "ACTIVE"));
		}
		ctx.fireChannelActive();
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "INACTIVE"));
		}
		ctx.fireChannelInactive();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(formatSimpleDetails(ctx, "EXCEPTION", cause), cause);
		}
		ctx.fireExceptionCaught(cause);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (this.logger.isTraceEnabled()) {
			if (this.includeData) {
				this.logger.trace(formatDetails(ctx, "USER_EVENT", evt));
			} else {
				this.logger.trace(formatSummary(ctx, "USER_EVENT", evt));
			}
		}
		ctx.fireUserEventTriggered(evt);
	}

	@Override
	public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(formatSimpleDetails(ctx, "BIND", localAddress));
		}
		ctx.bind(localAddress, promise);
	}

	@Override
	public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise)
		throws Exception {

		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "CONNECT", remoteAddress, localAddress));
		}
		ctx.connect(remoteAddress, localAddress, promise);
	}

	@Override
	public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "DISCONNECT"));
		}
		ctx.disconnect(promise);
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "CLOSE"));
		}
		ctx.close(promise);
	}

	@Override
	public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "DEREGISTER"));
		}
		ctx.deregister(promise);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "READ_COMPLETE"));
		}
		ctx.fireChannelReadComplete();
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (this.logger.isTraceEnabled()) {
			if (this.includeData) {
				this.logger.trace(formatDetails(ctx, "READ", msg));
			} else {
				this.logger.trace(formatSummary(ctx, "READ", msg));
			}
		}
		ctx.fireChannelRead(msg);
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (this.logger.isTraceEnabled()) {
			if (this.includeData) {
				this.logger.trace(formatDetails(ctx, "WRITE", msg));
			} else {
				this.logger.trace(formatSummary(ctx, "WRITE", msg));
			}
		}
		ctx.write(msg, promise);
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "WRITABILITY_CHANGED"));
		}
		ctx.fireChannelWritabilityChanged();
	}

	@Override
	public void flush(ChannelHandlerContext ctx) throws Exception {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(format(ctx, "FLUSH"));
		}
		ctx.flush();
	}

	/**
	 * Formats an event and returns the formatted message.
	 *
	 * @param eventName the name of the event
	 */
	protected String format(ChannelHandlerContext ctx, String eventName) {
		String chStr = ctx.channel().toString();
		return new StringBuilder(chStr.length() + 1 + eventName.length())
			.append(chStr)
			.append(' ')
			.append(eventName)
			.toString();
	}

	/**
	 * Formats an event and returns the formatted message.
	 *
	 * @param eventName the name of the event
	 * @param arg       the argument of the event
	 */
	protected String formatDetails(ChannelHandlerContext ctx, String eventName, Object arg) {
		if (arg instanceof ByteBuf) {
			return formatByteBufDetails(ctx, eventName, (ByteBuf) arg);
		} else if (arg instanceof ByteBufHolder) {
			return formatByteBufHolderDetails(ctx, eventName, (ByteBufHolder) arg);
		} else {
			return formatSimpleDetails(ctx, eventName, arg);
		}
	}

	/**
	 * Formats an event and returns the formatted message.
	 *
	 * @param eventName the name of the event
	 * @param arg       the argument of the event
	 */
	protected String formatSummary(ChannelHandlerContext ctx, String eventName, Object arg) {
		if (arg instanceof ByteBuf) {
			return formatByteBufSummary(ctx, eventName, (ByteBuf) arg);
		} else if (arg instanceof ByteBufHolder) {
			return formatByteBufHolderSummary(ctx, eventName, (ByteBufHolder) arg);
		} else {
			return formatSimpleSummary(ctx, eventName, arg);
		}
	}

	/**
	 * Formats an event and returns the formatted message. This method is currently
	 * only used for formatting
	 * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, ChannelPromise)}.
	 *
	 * @param eventName the name of the event
	 * @param firstArg  the first argument of the event
	 * @param secondArg the second argument of the event
	 */
	protected String format(ChannelHandlerContext ctx, String eventName, Object firstArg, Object secondArg) {
		if (secondArg == null) {
			return formatSimpleDetails(ctx, eventName, firstArg);
		}

		String chStr = ctx.channel().toString();
		String arg1Str = String.valueOf(firstArg);
		String arg2Str = secondArg.toString();
		StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + arg1Str.length() + 2 + arg2Str.length());
		buf.append(chStr).append(' ').append(eventName).append(": ").append(arg1Str).append(", ").append(arg2Str);
		return buf.toString();
	}

	/**
	 * Generates the default log message of the specified event whose argument is a
	 * {@link ByteBuf}.
	 */
	private static String formatByteBufDetails(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
		String chStr = ctx.channel().toString();
		int length = msg.readableBytes();
		if (length == 0) {
			StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 4);
			buf.append(chStr).append(' ').append(eventName).append(": 0B");
			return buf.toString();
		} else {
			int rows = (length + 15) / 16 + 4;
			StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + 10 + 1 + 2 + rows * 80);

			buf.append(chStr).append(' ').append(eventName).append(": ").append(length).append('B').append(NEWLINE);
			appendPrettyHexDump(buf, msg);

			return buf.toString();
		}
	}

	/**
	 * Generates the default log message of the specified event whose argument is a
	 * {@link ByteBuf}.
	 */
	private static String formatByteBufSummary(ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
		String chStr = ctx.channel().toString();
		int length = msg.readableBytes();
		StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 20);
		buf.append(chStr).append(' ').append(eventName).append(": ").append(length).append('B');
		return buf.toString();
	}

	/**
	 * Generates the default log message of the specified event whose argument is a
	 * {@link ByteBufHolder}.
	 */
	private static String formatByteBufHolderDetails(ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
		String chStr = ctx.channel().toString();
		String msgStr = msg.toString();
		ByteBuf content = msg.content();
		int length = content.readableBytes();
		if (length == 0) {
			StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 4);
			buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).append(", 0B");
			return buf.toString();
		} else {
			int rows = (length + 15) / 16 + 4;
			var buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 2 + 10 + 1 + 2 + rows * 80);
			buf
				.append(chStr)
				.append(' ')
				.append(eventName)
				.append(": ")
				.append(msgStr)
				.append(", ")
				.append(length)
				.append('B')
				.append(NEWLINE);
			appendPrettyHexDump(buf, content);

			return buf.toString();
		}
	}

	/**
	 * Generates the default log message of the specified event whose argument is a
	 * {@link ByteBufHolder}.
	 */
	private static String formatByteBufHolderSummary(ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
		String chStr = ctx.channel().toString();
		String msgStr = msg.toString();
		ByteBuf content = msg.content();
		int length = content.readableBytes();
		StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length() + 20);
		buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).append(", ").append(length).append('B');
		return buf.toString();
	}

	/**
	 * Generates the default log message of the specified event whose argument is an
	 * arbitrary object.
	 */
	private static String formatSimpleDetails(ChannelHandlerContext ctx, String eventName, Object msg) {
		String chStr = ctx.channel().toString();
		String msgStr = String.valueOf(msg);
		StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length());
		return buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).toString();
	}

	/**
	 * Generates the default log message of the specified event whose argument is an
	 * arbitrary object.
	 */
	private static String formatSimpleSummary(ChannelHandlerContext ctx, String eventName, Object msg) {
		String chStr = ctx.channel().toString();
		String msgStr = msg == null ? "null" : msg.getClass().getSimpleName();
		StringBuilder buf = new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + msgStr.length());
		return buf.append(chStr).append(' ').append(eventName).append(": ").append(msgStr).toString();
	}
}
