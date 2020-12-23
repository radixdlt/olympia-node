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

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.network.transport.TransportMetadata;

public final class PeerWithNid extends Peer {

	private final EUID nid;

	@Override
	public short VERSION() {
		return 100;
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

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PeerWithNid that = (PeerWithNid) o;
		return Objects.equals(nid, that.nid);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nid);
	}
}
