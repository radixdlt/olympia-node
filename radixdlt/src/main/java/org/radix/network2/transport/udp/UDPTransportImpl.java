package org.radix.network2.transport.udp;

import java.util.Objects;

import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;

public class UDPTransportImpl implements Transport {
	private final TransportMetadata metadata;
	private final TransportControl control;

	public UDPTransportImpl(TransportMetadata metadata, TransportControl control) {
		this.metadata = Objects.requireNonNull(metadata);
		this.control = Objects.requireNonNull(control);
	}

	@Override
	public String getName() {
		return UDPConstants.UDP_NAME;
	}

	@Override
	public TransportControl control() {
		return control;
	}

	@Override
	public TransportMetadata metadata() {
		return metadata;
	}

}
