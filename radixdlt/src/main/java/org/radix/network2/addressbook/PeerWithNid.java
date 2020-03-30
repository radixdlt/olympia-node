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

import java.util.stream.Stream;

import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.EUID;
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
final class PeerWithNid extends Peer {

	@JsonProperty("nid")
	@DsonOutput(Output.ALL)
	private final EUID nid;

	@Override
	public short VERSION() {
		return 100;
	}

	PeerWithNid() {
		// Serializer only
		this.nid = null;
	}

	PeerWithNid(EUID nid) {
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
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.nid);
	}

	// Note that we rely on equals(...) and hashCode() from BasicContainer here.
}
