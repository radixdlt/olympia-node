package org.radix.network2.transport.udp;

import java.io.IOException;

import org.radix.network2.transport.TransportMetadata;

public interface UDPSocketFactory {

	UDPSocket createServerSocket(TransportMetadata metadata) throws IOException;

	UDPChannel createClientChannel(TransportMetadata metadata) throws IOException;

}