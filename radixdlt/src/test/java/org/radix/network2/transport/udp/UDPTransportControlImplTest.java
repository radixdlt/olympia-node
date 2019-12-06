package org.radix.network2.transport.udp;

import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.TransportOutboundConnection;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UDPTransportControlImplTest {
    private UDPTransportOutboundConnectionFactory outboundFactory;
    private TransportOutboundConnection transportOutboundConnection;

    @Before
    public void setUp() {
        transportOutboundConnection = mock(TransportOutboundConnection.class);
        outboundFactory = mock(UDPTransportOutboundConnectionFactory.class);
        when(outboundFactory.create(any(), any())).thenReturn(transportOutboundConnection);
    }

    @Test
    public void open() throws ExecutionException, InterruptedException, IOException {
        try (UDPTransportControlImpl udpTransportControl = new UDPTransportControlImpl(null, outboundFactory)) {
        	CompletableFuture<TransportOutboundConnection> result = udpTransportControl.open(null);
        	assertThat(result.get()).isEqualTo(transportOutboundConnection);
        }
    }
}