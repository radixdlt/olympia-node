package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import com.radixdlt.common.EUID;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.udp.UDPConstants;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Full peer used when we have discovered full system information for the peer.
 */
@SerializerId2("network.peer")
public final class PeerWithSystem extends Peer {

	@Override
	public short VERSION() { return 100;}

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	/**
	 * Creates a peer with the specified system.
	 *
	 * @param system the system to associate with the peer
	 */
	public PeerWithSystem(RadixSystem system) {
		this.system = Objects.requireNonNull(system);
	}

	PeerWithSystem() {
		// Serializer only
		this(new RadixSystem());
	}

	PeerWithSystem(Peer toCopy, RadixSystem system) {
		super(toCopy); // Timestamps etc
		this.system = Objects.requireNonNull(system);
	}

	@Override
	public RadixSystem getSystem() {
		return this.system;
	}

	@Override
	public TransportMetadata connectionData(String transportName) {
		return system.supportedTransports()
			.filter(t -> t.name().equals(transportName))
			.findAny()
			.map(TransportInfo::metadata)
			.orElseThrow(() -> new TransportException(String.format("Peer %s has no transport %s", toString(), transportName)));
	}

	@Override
	public EUID getNID() {
		return this.system.getNID();
	}

	@Override
	public boolean supportsTransport(String transportName) {
		return system.supportedTransports()
			.map(TransportInfo::name)
			.anyMatch(transportName::equals);
	}

	@Override
	public Stream<TransportInfo> supportedTransports() {
		return system.supportedTransports();
	}

	@Override
	public boolean hasNID() {
		return true;
	}

	@Override
	public boolean hasSystem() {
		return true;
	}

	@Override
	public String toString() {
		String connectionInfo = supportsTransport(UDPConstants.UDP_NAME) ? connectionData(UDPConstants.UDP_NAME).toString() : "(No UDP data)";
		return String.format("%s[%s:%s]", this.getClass().getSimpleName(), this.system.getNID(), connectionInfo);
	}

	// Note that we rely on equals(...) and hashCode() from BasicContainer here.
}
