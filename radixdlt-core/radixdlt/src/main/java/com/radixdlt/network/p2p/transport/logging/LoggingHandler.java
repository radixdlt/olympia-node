/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p.transport.logging;

import static io.netty.buffer.ByteBufUtil.appendPrettyHexDump;
import static io.netty.util.internal.StringUtil.NEWLINE;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.net.SocketAddress;
import java.util.Objects;

/** A {@link io.netty.channel.ChannelHandler} that logs all events using a supplied log sink. */
@Sharable
public class LoggingHandler extends ChannelDuplexHandler {

  private final LogSink logger;
  private final boolean includeData;

  /**
   * Creates a new instance where log messages are sent to the specified {@link LogSink}.
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
  public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise)
      throws Exception {
    if (this.logger.isTraceEnabled()) {
      this.logger.trace(formatSimpleDetails(ctx, "BIND", localAddress));
    }
    ctx.bind(localAddress, promise);
  }

  @Override
  public void connect(
      ChannelHandlerContext ctx,
      SocketAddress remoteAddress,
      SocketAddress localAddress,
      ChannelPromise promise)
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
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
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
    return ctx.channel().toString() + ' ' + eventName;
  }

  /**
   * Formats an event and returns the formatted message.
   *
   * @param eventName the name of the event
   * @param arg the argument of the event
   */
  protected String formatDetails(ChannelHandlerContext ctx, String eventName, Object arg) {
    if (arg instanceof ByteBuf byteBuf) {
      return formatByteBufDetails(ctx, eventName, byteBuf);
    } else if (arg instanceof ByteBufHolder byteBufHolder) {
      return formatByteBufHolderDetails(ctx, eventName, byteBufHolder);
    } else {
      return formatSimpleDetails(ctx, eventName, arg);
    }
  }

  /**
   * Formats an event and returns the formatted message.
   *
   * @param eventName the name of the event
   * @param arg the argument of the event
   */
  protected String formatSummary(ChannelHandlerContext ctx, String eventName, Object arg) {
    if (arg instanceof ByteBuf byteBuf) {
      return formatByteBufSummary(ctx, eventName, byteBuf);
    } else if (arg instanceof ByteBufHolder byteBufHolder) {
      return formatByteBufHolderSummary(ctx, eventName, byteBufHolder);
    } else {
      return formatSimpleSummary(ctx, eventName, arg);
    }
  }

  /**
   * Formats an event and returns the formatted message. This method is currently only used for
   * formatting {@link io.netty.channel.ChannelOutboundHandler#connect(ChannelHandlerContext,
   * SocketAddress, SocketAddress, ChannelPromise)}.
   *
   * @param eventName the name of the event
   * @param firstArg the first argument of the event
   * @param secondArg the second argument of the event
   */
  protected String format(
      ChannelHandlerContext ctx, String eventName, Object firstArg, Object secondArg) {
    if (secondArg == null) {
      return formatSimpleDetails(ctx, eventName, firstArg);
    }

    return ctx.channel().toString() + ' ' + eventName + ": " + firstArg + ", " + secondArg;
  }

  /**
   * Generates the default log message of the specified event whose argument is a {@link ByteBuf}.
   */
  private static String formatByteBufDetails(
      ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
    var chStr = ctx.channel().toString();
    var length = msg.readableBytes();

    if (length == 0) {
      return chStr + ' ' + eventName + ": 0B";
    } else {
      var rows = (length + 15) / 16 + 4;
      var buf =
          new StringBuilder(chStr.length() + 1 + eventName.length() + 2 + 10 + 1 + 2 + rows * 80);

      buf.append(chStr)
          .append(' ')
          .append(eventName)
          .append(": ")
          .append(length)
          .append('B')
          .append(NEWLINE);
      appendPrettyHexDump(buf, msg);

      return buf.toString();
    }
  }

  /**
   * Generates the default log message of the specified event whose argument is a {@link ByteBuf}.
   */
  private static String formatByteBufSummary(
      ChannelHandlerContext ctx, String eventName, ByteBuf msg) {
    return ctx.channel().toString() + ' ' + eventName + ": " + msg.readableBytes() + 'B';
  }

  /**
   * Generates the default log message of the specified event whose argument is a {@link
   * ByteBufHolder}.
   */
  private static String formatByteBufHolderDetails(
      ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
    var chStr = ctx.channel().toString();
    var msgStr = msg.toString();
    var content = msg.content();
    var length = content.readableBytes();

    if (length == 0) {
      return chStr + ' ' + eventName + ": " + msgStr + ", 0B";
    } else {
      var rows = (length + 15) / 16 + 4;
      var buf =
          new StringBuilder(
              chStr.length()
                  + 1
                  + eventName.length()
                  + 2
                  + msgStr.length()
                  + 2
                  + 10
                  + 1
                  + 2
                  + rows * 80);
      buf.append(chStr)
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
   * Generates the default log message of the specified event whose argument is a {@link
   * ByteBufHolder}.
   */
  private static String formatByteBufHolderSummary(
      ChannelHandlerContext ctx, String eventName, ByteBufHolder msg) {
    return ctx.channel().toString()
        + ' '
        + eventName
        + ": "
        + msg.toString()
        + ", "
        + msg.content().readableBytes()
        + 'B';
  }

  /**
   * Generates the default log message of the specified event whose argument is an arbitrary object.
   */
  private static String formatSimpleDetails(
      ChannelHandlerContext ctx, String eventName, Object msg) {
    return ctx.channel().toString() + ' ' + eventName + ": " + msg;
  }

  /**
   * Generates the default log message of the specified event whose argument is an arbitrary object.
   */
  private static String formatSimpleSummary(
      ChannelHandlerContext ctx, String eventName, Object msg) {
    var msgStr = msg == null ? "null" : msg.getClass().getSimpleName();
    return ctx.channel().toString() + ' ' + eventName + ": " + msgStr;
  }
}
