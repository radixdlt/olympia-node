package org.radix.network2.transport.udp;

import java.io.IOException;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportFactory;
import org.radix.network2.transport.TransportMetadata;

public final class UDPTransportFactoryImpl implements TransportFactory {

	public final UDPSocketFactory socketFactory;

	public UDPTransportFactoryImpl(UDPSocketFactory socketFactory) {
		this.socketFactory = socketFactory;
	}

	@Override
	@SuppressWarnings("resource")
	// Resource warning suppression OK here -> caller is responsible
	public Transport create(TransportMetadata metadata) throws IOException {
		// Connectionless -> use the same "connection" for each opened connection
		UDPTransportOutboundConnection outbound = new UDPTransportOutboundConnection(metadata, socketFactory);
		return new UDPTransportImpl(metadata, new UDPTransportControlImpl(outbound));
	}

}
