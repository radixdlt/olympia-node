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

@SerializerId2("network.peer")
public final class PeerImpl extends Peer {

	@Override
	public short VERSION() { return 100;}

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	public PeerImpl() {
		this(new RadixSystem());
	}

	public PeerImpl(RadixSystem system) {
		this.system = Objects.requireNonNull(system);
	}

	PeerImpl(Peer toCopy, RadixSystem system) {
		super(toCopy);
		this.system = Objects.requireNonNull(system);
	}

	@Override
	public final boolean equals(Object object)
	{
		if (object == this) {
			return true;
		}

		if (object instanceof PeerImpl) {
			PeerImpl other = (PeerImpl) object;
			return Objects.equals(this.system.getNID(), other.system.getNID());
		}

		return false;
	}

	@Override
	public final int hashCode()
	{
		return this.system.getNID().hashCode();
	}

	@Override
	public String toString()
	{
		return String.format("%s[%s:%s]", this.getClass().getSimpleName(), this.system.getNID(), connectionData(UDPConstants.UDP_NAME));
	}

	@Override
	public RadixSystem getSystem()
	{
		return this.system;
	}

	@Override
	public void setSystem(RadixSystem system)
	{
		this.system = system;
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
	// FIXME temporary until address book is sorted
	public boolean supportsTransport(String transportName) {
		return !system.supportedTransports()
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
}
