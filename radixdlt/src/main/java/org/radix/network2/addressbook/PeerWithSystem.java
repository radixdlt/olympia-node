/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network2.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import com.radixdlt.identifiers.EUID;
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
			.orElseThrow(() -> new TransportException(String.format("Peer %s has no transport %s", this.getNID(), transportName)));
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
