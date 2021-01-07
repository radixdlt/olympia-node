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

import io.netty.channel.socket.DatagramChannel;

import org.junit.Test;
import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportMetadata;

import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class UDPTransportOutboundConnectionTest2 {
	@Test
	public void sendTooLong() throws ExecutionException, InterruptedException, IOException {
		DatagramChannel channel = mock(DatagramChannel.class);
		TransportMetadata metadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_HOST, "localhost",
			UDPConstants.METADATA_PORT, "443"
		);
		InetAddress sourceAddress = mock(InetAddress.class);
		when(sourceAddress.getAddress()).thenReturn(new byte[4]);
		NatHandler natHandler = mock(NatHandler.class);
		when(natHandler.getAddress()).thenReturn(sourceAddress);
		try (var udpTransportOutboundConnection = new UDPTransportOutboundConnection(channel, metadata, natHandler)) {
			byte[] data = new byte[UDPConstants.MAX_PACKET_LENGTH + 1];
			CompletableFuture<SendResult> completableFuture = udpTransportOutboundConnection.send(data);

			assertThat(completableFuture.isDone()).isEqualTo(true);

			SendResult result = completableFuture.get();
			assertThat(result.isComplete()).isEqualTo(false);
			assertThat(result.getThrowable().getMessage()).contains("is too large");
		}
	}

	@Test
	public void sensibleToString() {
		TransportMetadata metadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_HOST, "localhost",
			UDPConstants.METADATA_PORT, "443"
		);
		DatagramChannel channel = mock(DatagramChannel.class);
		NatHandler natHandler = mock(NatHandler.class);

		// No resource issues as everything is mocked
		@SuppressWarnings("resource")
		UDPTransportOutboundConnection oci = new UDPTransportOutboundConnection(channel, metadata, natHandler);
		String s = oci.toString();

		assertThat(s, containsString(UDPConstants.NAME));
		assertThat(s, containsString(metadata.get(UDPConstants.METADATA_HOST)));
		assertThat(s, containsString(metadata.get(UDPConstants.METADATA_PORT)));
	}
}