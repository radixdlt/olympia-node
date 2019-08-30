package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.universe.system.RadixSystem;

import com.radixdlt.common.EUID;
import com.radixdlt.serialization.SerializerId2;

@SerializerId2("network.peer.transport")
final class UriOnlyPeer extends Peer {
	private final TransportInfo transportInfo;

	@Override
	public short VERSION() {
		return 100;
	}

	UriOnlyPeer() {
		// Serializer only
		this.transportInfo = null;
	}

	UriOnlyPeer(TransportInfo transportInfo) {
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
	public void setSystem(RadixSystem system) {
		// FIXME: In fact, shouldn't be able to do this at all
		throw new IllegalStateException("Can't set system on stub peer");
	}

	@Override
	public int hashCode() {
		return this.transportInfo.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof UriOnlyPeer) {
			UriOnlyPeer other = (UriOnlyPeer) obj;
			return Objects.equals(this.transportInfo, other.transportInfo);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), transportInfo);
	}
}
