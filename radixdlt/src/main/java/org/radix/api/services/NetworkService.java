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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.hash.HashCode;
import com.radixdlt.crypto.Hasher;
import org.json.JSONArray;
import org.json.JSONObject;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.universe.system.LocalSystem;

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
 		JSONObject self = new JSONObject();
		self.put("system", serialization.toJsonObject(localSystem, Output.WIRE));
 		return self;
 	}

 	public JSONObject getNetwork() {
		JSONObject result = new JSONObject();

		// sorted by transport name
		Map<String, Set<Peer>> peersByTransport = new TreeMap<>();

		selfAndOthers(this.addressBook.recentPeers())
			.forEachOrdered(p -> addPeerToMap(p, peersByTransport));

		for (Map.Entry<String, Set<Peer>> e : peersByTransport.entrySet()) {
			JSONArray transportPeers = new JSONArray();
			for (Peer peer : e.getValue()) {
				transportPeers.put(serialization.toJsonObject(peer, Output.API));
			}
			result.put(e.getKey(), transportPeers);
		}

		return result;
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
		JSONArray nids = new JSONArray();
		selfAndOthers(this.addressBook.recentPeers()).forEachOrdered(peer -> nids.put(peer.getNID().toString()));
		return new JSONObject().put("nids", nids);
	}

	public List<JSONObject> getPeers() {
		return selfAndOthers(this.addressBook.peers())
			.map(peer -> serialization.toJsonObject(peer, Output.WIRE))
			.collect(Collectors.toList());
	}

	public JSONObject getPeer(String id) {
		try {
			EUID euid = EUID.valueOf(id);
			if (euid.equals(EUID.fromHash(this.localPeerHash))) {
				return serialization.toJsonObject(this.localPeer, Output.API);
			}
			return this.addressBook.peer(euid)
				.map(peer -> serialization.toJsonObject(peer, Output.API))
				.orElseGet(JSONObject::new);
		} catch (IllegalArgumentException ex) {
			// Ignore, return empty object
		}
		return new JSONObject();
	}

	private Stream<Peer> selfAndOthers(Stream<Peer> others) {
		return Stream.concat(Stream.of(this.localPeer), others).distinct();
	}
}
