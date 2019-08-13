package org.radix.network2.transport.udp;

import java.io.IOException;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;

class UDPTransportImpl implements Transport {
	private final TransportMetadata metadata;
	private final TransportControl control;

	UDPTransportImpl(TransportMetadata metadata, TransportControl control) {
		this.metadata = metadata;
		this.control = control;
	}

	@Override
	public String name() {
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

	@Override
	public void close() throws IOException {
		control.close();
	}
}
