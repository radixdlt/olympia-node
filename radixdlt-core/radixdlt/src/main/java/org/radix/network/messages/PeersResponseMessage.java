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

package org.radix.network.messages;

import java.util.Objects;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.network.p2p.RadixNodeUri;
import org.radix.network.messaging.Message;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("p2p.discovery.peers_response")
public final class PeersResponseMessage extends Message {
	@JsonProperty("peers")
	@DsonOutput(Output.ALL)
	private final ImmutableSet<RadixNodeUri> peers;

	// For serializer
	private PeersResponseMessage() {
		super(0);
		peers = ImmutableSet.of();
	}

	public PeersResponseMessage(int magic) {
		this(magic, ImmutableSet.of());
	}

	public PeersResponseMessage(int magic, ImmutableSet<RadixNodeUri> peers) {
		super(magic);

		this.peers = peers;
	}

	public ImmutableSet<RadixNodeUri> getPeers() {
		return peers;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), peers);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final var that = (PeersResponseMessage) o;
		return Objects.equals(peers, that.peers)
			&& Objects.equals(getTimestamp(), that.getTimestamp())
			&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(peers, getTimestamp(), getMagic());
	}
}
