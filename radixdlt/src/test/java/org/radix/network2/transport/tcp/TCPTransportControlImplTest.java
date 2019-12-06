package org.radix.network2.transport.tcp;

import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public class TCPTransportControlImplTest {
	private NettyTCPTransport transport;
    private TCPTransportOutboundConnectionFactory outboundFactory;
    private TransportOutboundConnection transportOutboundConnection;

    @Before
    public void setUp() {
        transportOutboundConnection = mock(TransportOutboundConnection.class);

        outboundFactory = mock(TCPTransportOutboundConnectionFactory.class);
        when(outboundFactory.create(any(), any())).thenReturn(transportOutboundConnection);

        Channel ch = mock(Channel.class);
        ChannelFuture cf = mock(ChannelFuture.class);
        when(cf.addListener(any())).thenAnswer(a -> {
        	GenericFutureListener<Future<Void>> listener = a.getArgument(0);
        	listener.operationComplete(cf);
        	return cf;
        });
        when(cf.channel()).thenReturn(ch);

        transport = mock(NettyTCPTransport.class);
    	when(transport.createChannel(any(), anyInt())).thenReturn(cf);
    }

    @Test
    public void open() throws ExecutionException, InterruptedException, IOException {
        try (TCPTransportControlImpl tcpTransportControl = new TCPTransportControlImpl(outboundFactory, transport)) {
        	TransportMetadata metadata = StaticTransportMetadata.of(
        		TCPConstants.METADATA_TCP_HOST, "localhost",
        		TCPConstants.METADATA_TCP_PORT, "443"
        	);
        	CompletableFuture<TransportOutboundConnection> result = tcpTransportControl.open(metadata);
        	assertThat(result.get()).isEqualTo(transportOutboundConnection);
        }
    }
}