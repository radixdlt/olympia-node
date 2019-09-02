package org.radix.network2.addressbook;

import java.util.stream.Stream;

import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * Stub peer used when we only have a transport method for the peer.
 * Typically used for discovery when nothing else substantial is known
 * about the peer, and ideally will be replaced relatively quickly with
 * a peer type with full system information.
 */
@SerializerId2("network.peer.transport")
final class PeerWithTransport extends Peer {

	@JsonProperty("transport")
	@DsonOutput(Output.ALL)
	private final TransportInfo transportInfo;

	@Override
	public short VERSION() {
		return 100;
	}

	PeerWithTransport() {
		// Serializer only
		this.transportInfo = null;
	}

	PeerWithTransport(TransportInfo transportInfo) {
		this.transportInfo = transportInfo;
	}

	@Override
	public EUID getNID() {
		return EUID.ZERO;
	}

	@Override
	public boolean hasNID() {
		return false;
	}

	@Override
	public boolean supportsTransport(String transportName) {
		return this.transportInfo.name().equals(transportName);
	}

	@Override
	public Stream<TransportInfo> supportedTransports() {
		return Stream.of(this.transportInfo);
	}

	@Override
	public TransportMetadata connectionData(String transportName) {
		if (!this.transportInfo.name().equals(transportName) ) {
			throw new TransportException(String.format("Peer %s has no transport %s", toString(), transportName));
		}
		return this.transportInfo.metadata();
	}

	@Override
	public boolean hasSystem() {
		return false; // No system
	}

	@Override
	public RadixSystem getSystem() {
		return null; // No system
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), transportInfo);
	}

	// Note that we rely on equals(...) and hashCode() from BasicContainer here.
}
