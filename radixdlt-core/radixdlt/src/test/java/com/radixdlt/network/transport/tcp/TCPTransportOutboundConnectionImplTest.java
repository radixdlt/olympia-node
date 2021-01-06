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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Test;

import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportMetadata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class TCPTransportOutboundConnectionImplTest {

	@Test
	public void testClose() throws IOException, InterruptedException {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		ChannelFuture cf = mock(ChannelFuture.class);
		Channel channel = mock(Channel.class);
		when(channel.close()).thenReturn(cf);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		oci.close();

		verify(channel, times(1)).close();
		verify(cf, times(1)).sync();
	}

	@Test
	public void testCloseFail() throws InterruptedException {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		ChannelFuture cf = mock(ChannelFuture.class);
		when(cf.sync()).thenThrow(new InterruptedException());
		Channel channel = mock(Channel.class);
		when(channel.close()).thenReturn(cf);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		assertThatThrownBy(oci::close)
			.isInstanceOf(IOException.class);
	}

	@Test
	public void testSend() throws InterruptedException, ExecutionException {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		ByteBufAllocator bba = mock(ByteBufAllocator.class);
		when(bba.directBuffer(anyInt())).thenAnswer(inv -> Unpooled.buffer(inv.getArgument(0)));
		ChannelFuture cf = mock(ChannelFuture.class);
		when(cf.addListener(any())).thenAnswer(a -> {
			GenericFutureListener<Future<Void>> listener = a.getArgument(0);
			listener.operationComplete(cf);
			return cf;
		});
		Channel channel = mock(Channel.class);
		when(channel.alloc()).thenReturn(bba);
		when(channel.writeAndFlush(any())).thenReturn(cf);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		byte[] data = new byte[20];
		CompletableFuture<SendResult> cfsr = oci.send(data);
		assertTrue(cfsr.isDone());
		assertTrue(cfsr.get().isComplete());
	}

	@Test
	public void testSendFail() throws InterruptedException, ExecutionException {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		ByteBufAllocator bba = mock(ByteBufAllocator.class);
		when(bba.directBuffer(anyInt())).thenAnswer(inv -> Unpooled.buffer(inv.getArgument(0)));
		ChannelFuture cf = mock(ChannelFuture.class);
		IOException dummyException = new IOException("dummy exception");
		when(cf.cause()).thenReturn(dummyException);
		when(cf.addListener(any())).thenAnswer(a -> {
			GenericFutureListener<Future<Void>> listener = a.getArgument(0);
			listener.operationComplete(cf);
			return cf;
		});
		Channel channel = mock(Channel.class);
		when(channel.alloc()).thenReturn(bba);
		when(channel.writeAndFlush(any())).thenReturn(cf);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		byte[] data = new byte[20];
		CompletableFuture<SendResult> cfsr = oci.send(data);
		assertTrue(cfsr.isDone());
		assertFalse(cfsr.get().isComplete());
		assertSame(dummyException, cfsr.get().getThrowable());
	}

	@Test
	public void testSendTooLong() throws InterruptedException, ExecutionException {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		ByteBufAllocator bba = mock(ByteBufAllocator.class);
		when(bba.directBuffer(anyInt())).thenAnswer(inv -> Unpooled.buffer(inv.getArgument(0)));
		ChannelFuture cf = mock(ChannelFuture.class);
		when(cf.addListener(any())).thenAnswer(a -> {
			GenericFutureListener<Future<Void>> listener = a.getArgument(0);
			listener.operationComplete(cf);
			return cf;
		});
		Channel channel = mock(Channel.class);
		when(channel.alloc()).thenReturn(bba);
		when(channel.writeAndFlush(any())).thenReturn(cf);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		byte[] data = new byte[TCPConstants.MAX_PACKET_LENGTH + 1];
		CompletableFuture<SendResult> cfsr = oci.send(data);
		assertTrue(cfsr.isDone());
		assertFalse(cfsr.get().isComplete());
	}

	@Test
	public void sensibleToString() {
		TransportMetadata metadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_HOST, "localhost",
			TCPConstants.METADATA_PORT, "443"
		);
		Channel channel = mock(Channel.class);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		TCPTransportOutboundConnectionImpl oci = new TCPTransportOutboundConnectionImpl(channel, metadata);
		String s = oci.toString();

		assertThat(s, containsString(TCPConstants.NAME));
		assertThat(s, containsString(metadata.get(TCPConstants.METADATA_HOST)));
		assertThat(s, containsString(metadata.get(TCPConstants.METADATA_PORT)));
	}
}
