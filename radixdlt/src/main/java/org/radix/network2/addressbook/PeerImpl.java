package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;

public final class PeerImpl implements Peer {

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
		return supportedTransports()
			.map(TransportInfo::name)
			.filter(transportName::equals)
			.findAny()
			.isPresent();
	}

	@Override
	public TransportMetadata connectionData(String transportName) {
		return supportedTransports()
			.filter(t -> t.name().equals(transportName))
			.map(TransportInfo::metadata)
			.findAny()
			.orElseThrow(() -> new TransportException("Transport " + transportName + " not supported"));
	}

	@Override
	public Stream<TransportInfo> supportedTransports() {
		return system.supportedTransports();
	}
}
