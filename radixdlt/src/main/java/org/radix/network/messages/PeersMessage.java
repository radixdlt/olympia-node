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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.radix.network.messaging.Message;

import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("network.message.peers")
public final class PeersMessage extends Message
{
	@JsonProperty("peers")
	@DsonOutput(Output.ALL)
	private List<Peer> peers = new ArrayList<>();

	private PeersMessage() {
		super(0);
		// For serializer
	}

	public PeersMessage(int magic) {
		super(magic);
	}

	public List<Peer> getPeers() {
		return peers;
	}

	public void setPeers(Collection<Peer> peers) {
		this.peers.clear();
		this.peers.addAll(peers);
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), peers);
	}
}
