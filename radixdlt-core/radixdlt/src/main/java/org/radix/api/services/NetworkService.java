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

package org.radix.api.services;

import org.json.JSONObject;
import org.radix.universe.system.LocalSystem;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class NetworkService {
	private final Serialization serialization;
	private final LocalSystem localSystem;
	private final AddressBook addressBook;
	private final PeerWithSystem localPeer;
	private final HashCode localPeerHash;

	public NetworkService(Serialization serialization, LocalSystem localSystem, AddressBook addressBook, Hasher hasher) {
		this.serialization = serialization;
		this.localSystem = localSystem;
		this.addressBook = addressBook;

		this.localPeer = new PeerWithSystem(this.localSystem);
		this.localPeerHash = hasher.hash(localPeer);
	}

	public JSONObject getSelf() {
		return jsonObject()
			.put("system", serialization.toJsonObject(localSystem, Output.WIRE));
 	}

 	public JSONObject getNetwork() {
		var result = jsonObject();
		var peersByTransport = new TreeMap<String, Set<Peer>>(); // sorted by transport name

		selfAndOthers(this.addressBook.recentPeers())
			.forEachOrdered(p -> addPeerToMap(p, peersByTransport));

		peersByTransport.entrySet().forEach(entry -> processEntry(result, entry));

		return result;
	}

	private void processEntry(final JSONObject result, final Map.Entry<String, Set<Peer>> entry) {
		var transportPeers = jsonArray();

		entry.getValue().stream()
			.map(peer -> serialization.toJsonObject(peer, Output.API))
			.forEach(transportPeers::put);

		result.put(entry.getKey(), transportPeers);
	}

	private void addPeerToMap(Peer peer, Map<String, Set<Peer>> pbt) {
		peer.supportedTransports()
			.forEachOrdered(ti -> pbt.computeIfAbsent(ti.name(), k -> new HashSet<>()).add(peer));
	}

	public List<JSONObject> getLivePeers() {
		return selfAndOthers(this.addressBook.recentPeers())
			.map(peer -> serialization.toJsonObject(peer, Output.WIRE))
			.collect(Collectors.toList());
	}

	public JSONObject getLiveNIDS() {
		var nids = jsonArray();
		selfAndOthers(this.addressBook.recentPeers()).forEachOrdered(peer -> nids.put(peer.getNID().toString()));
		return jsonObject().put("nids", nids);
	}

	public List<JSONObject> getPeers() {
		return selfAndOthers(this.addressBook.peers())
			.map(peer -> serialization.toJsonObject(peer, Output.WIRE))
			.collect(Collectors.toList());
	}

	public JSONObject getPeer(String id) {
		try {
			var euid = EUID.valueOf(id);
			if (euid.equals(EUID.fromHash(this.localPeerHash))) {
				return serialization.toJsonObject(this.localPeer, Output.API);
			}
			return this.addressBook.peer(euid)
				.map(peer -> serialization.toJsonObject(peer, Output.API))
				.orElseGet(JSONObject::new);
		} catch (IllegalArgumentException ex) {
			// Ignore, return empty object
		}
		return jsonObject();
	}

	private Stream<PeerWithSystem> selfAndOthers(Stream<PeerWithSystem> others) {
		return Stream.concat(Stream.of(this.localPeer), others).distinct();
	}
}
