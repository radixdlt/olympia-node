/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.network.p2p.discovery;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.network.p2p.RadixNodeUri;

import java.util.Objects;

public final class PeersResponse {

	private final ImmutableSet<RadixNodeUri> peers;

	public static PeersResponse create(ImmutableSet<RadixNodeUri> peers) {
		return new PeersResponse(peers);
	}

	private PeersResponse(ImmutableSet<RadixNodeUri> peers) {
		this.peers = peers;
	}

	public ImmutableSet<RadixNodeUri> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return String.format("%s{}", this.getClass().getSimpleName());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (PeersResponse) o;
		return Objects.equals(peers, that.peers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(peers);
	}
}
