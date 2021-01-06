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

package com.radixdlt.network.addressbook;

import java.util.Objects;
import java.util.stream.Stream;

import org.radix.universe.system.RadixSystem;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;
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
public final class PeerWithTransport extends Peer {

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

	public PeerWithTransport(TransportInfo transportInfo) {
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
		if (!this.transportInfo.name().equals(transportName)) {
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PeerWithTransport that = (PeerWithTransport) o;
		return Objects.equals(transportInfo, that.transportInfo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(transportInfo);
	}
}
