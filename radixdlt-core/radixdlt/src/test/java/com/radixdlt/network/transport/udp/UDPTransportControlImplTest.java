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

import org.junit.Before;
import org.junit.Test;

import com.radixdlt.network.transport.TransportOutboundConnection;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.socket.DatagramChannel;

public class UDPTransportControlImplTest {
    private UDPTransportOutboundConnectionFactory outboundFactory;
    private TransportOutboundConnection transportOutboundConnection;

    @Before
    public void setUp() {
        transportOutboundConnection = mock(TransportOutboundConnection.class);
        outboundFactory = mock(UDPTransportOutboundConnectionFactory.class);
        when(outboundFactory.create(any(), any(), any())).thenReturn(transportOutboundConnection);
    }

    @Test
    public void open() throws ExecutionException, InterruptedException, IOException {
    	DatagramChannel ch = mock(DatagramChannel.class);
    	NatHandler natHandler = mock(NatHandler.class);
        try (UDPTransportControlImpl udpTransportControl = new UDPTransportControlImpl(ch, outboundFactory, natHandler)) {
        	CompletableFuture<TransportOutboundConnection> result = udpTransportControl.open(null);
        	assertThat(result.get()).isEqualTo(transportOutboundConnection);
        }
    }
}