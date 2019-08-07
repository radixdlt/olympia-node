package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import org.radix.network2.transport.ConnectionData;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportException;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

public class PeerImpl implements Peer {

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	@JsonProperty("timestamps")
	@DsonOutput(Output.ALL)
	private PeerTimestamps timestamps;

	@VisibleForTesting
	PeerImpl(RadixSystem system) {
		this(system, PeerTimestamps.never());
	}

	@VisibleForTesting
	PeerImpl(RadixSystem system, PeerTimestamps timestamps) {
		this.system = Objects.requireNonNull(system);
		this.timestamps = Objects.requireNonNull(timestamps);
	}

	@Override
	public EUID getNID() {
		return system.getNID();
	}

	@Override
	public PeerTimestamps getTimestamps() {
		return this.timestamps;
	}

	@Override
	public boolean supportsTransport(String transportName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Stream<Transport> supportedTransports() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ConnectionData connectionData(Transport transport) throws TransportException {
		// TODO Auto-generated method stub
		return null;
	}
}
