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

package com.radixdlt.network.p2p.transport;

import static org.mockito.Mockito.*;

import com.radixdlt.network.p2p.transport.logging.LogSink;
import com.radixdlt.network.p2p.transport.logging.LoggingHandler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.StringUtil;
import java.net.SocketAddress;
import org.junit.Before;
import org.junit.Test;

/** Tests for Netty logging handler. */
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
    verify(logger, times(1))
        .trace(this.channel.toString() + " EXCEPTION: java.lang.Exception: test exception", ex);
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testUserEventTriggeredChannelHandlerContextObject() throws Exception {
    Object randomObject = new Object();
    this.handler.userEventTriggered(ctx, randomObject);

    verify(logger, times(1)).isTraceEnabled();
    verify(logger, times(1))
        .trace(this.channel.toString() + " USER_EVENT: " + randomObject.toString());
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
  public void testConnectChannelHandlerContextSocketAddressSocketAddressChannelPromise()
      throws Exception {
    SocketAddress addr1 = mock(SocketAddress.class);
    SocketAddress addr2 = mock(SocketAddress.class);
    ChannelPromise promise = mock(ChannelPromise.class);
    this.handler.connect(ctx, addr1, addr2, promise);

    verify(logger, times(1)).isTraceEnabled();
    verify(logger, times(1))
        .trace(this.channel.toString() + " CONNECT: " + addr1.toString() + ", " + addr2.toString());
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
    byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'};
    ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
    this.handler.userEventTriggered(ctx, byteBuf);

    String newline = StringUtil.NEWLINE;
    StringBuilder sb =
        new StringBuilder(this.channel.toString())
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
    verify(logger, times(1))
        .trace(this.channel.toString() + " USER_EVENT: " + byteBufHolder.toString() + ", 0B");
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testUserEventTriggeredChannelHandlerContextByteBufHolder() throws Exception {
    byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'};
    ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
    ByteBufHolder byteBufHolder = new DefaultByteBufHolder(byteBuf);
    this.handler.userEventTriggered(ctx, byteBufHolder);

    String newline = StringUtil.NEWLINE;
    StringBuilder sb =
        new StringBuilder(this.channel.toString())
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
    byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'};
    ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
    nologHandler.userEventTriggered(ctx, byteBuf);

    verify(logger, times(1)).isTraceEnabled();
    verify(logger, times(1)).trace(this.channel.toString() + " USER_EVENT: " + bytes.length + "B");
    verifyNoMoreInteractions(logger);
  }

  @Test
  public void testUserEventByteBufHolderNoDetails() throws Exception {
    LoggingHandler nologHandler = new LoggingHandler(this.logger, false);
    byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 'A', 'B', 'C', 'D'};
    ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
    ByteBufHolder byteBufHolder = new DefaultByteBufHolder(byteBuf);
    nologHandler.userEventTriggered(ctx, byteBufHolder);

    verify(logger, times(1)).isTraceEnabled();
    verify(logger, times(1))
        .trace(
            this.channel.toString()
                + " USER_EVENT: "
                + byteBufHolder.toString()
                + ", "
                + bytes.length
                + "B");
    verifyNoMoreInteractions(logger);
  }
}
