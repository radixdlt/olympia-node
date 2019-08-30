package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

/**
 * Stub peer used when we only have a NID for the peer.
 * Here so we can satisfy Tempo2 requirements to remember NIDs received
 * in temporal proofs.  Will be replaced with a peer type with full system
 * information once this has been discovered.
 */
@SerializerId2("network.peer.nid")
final class NidOnlyPeer extends Peer {

	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	private final EUID nid;

	@Override
	public short VERSION() {
		return 100;
	}

	NidOnlyPeer() {
		// Serializer only
		this.nid = null;
	}

	NidOnlyPeer(EUID nid) {
		this.nid = nid;
	}

	@Override
	public EUID getNID() {
		return this.nid;
	}

	@Override
	public boolean hasNID() {
		return true;
	}

	@Override
	public boolean supportsTransport(String transportName) {
		return false; // No known transports
	}

	@Override
	public Stream<TransportInfo> supportedTransports() {
		return Stream.empty(); // No known transports
	}

	@Override
	public TransportMetadata connectionData(String transportName) {
		throw new TransportException(String.format("Peer %s has no transport %s", this.nid, transportName));
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
	public int hashCode() {
		return this.nid.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof NidOnlyPeer) {
			NidOnlyPeer other = (NidOnlyPeer) obj;
			return Objects.equals(this.nid, other.nid);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.nid);
	}
}
