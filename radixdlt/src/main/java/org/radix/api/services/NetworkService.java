package org.radix.api.services;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.radixdlt.common.EUID;
import org.radix.modules.Modules;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemMetaData;

public class NetworkService {
	private final Serialization serialization;
	private final LocalSystem localSystem;
	private final AddressBook addressBook;

	public NetworkService(Serialization serialization, LocalSystem localSystem, AddressBook addressBook) {
		this.serialization = serialization;
		this.localSystem = localSystem;
		this.addressBook = addressBook;
	}

	public JSONObject getSelf() {
 		JSONObject self = new JSONObject();
		self.put("system", serialization.toJsonObject(localSystem, Output.WIRE));
 		return self;
 	}

 	public JSONObject getNetwork() throws JSONException {
		JSONObject result = new JSONObject();

		// sorted by transport name
		Map<String, Set<Peer>> peersByTransport = new TreeMap<>();

		this.addressBook.recentPeers()
			.forEachOrdered(p -> addPeerToMap(p, peersByTransport));


		for (Map.Entry<String, Set<Peer>> e : peersByTransport.entrySet()) {
			JSONArray transportPeers = new JSONArray();
			for (Peer peer : e.getValue()) {
				transportPeers.put(serialization.toJsonObject(peer, Output.API));
			}
			result.put(e.getKey(), transportPeers);
		}
		SystemMetaData.ifPresent( a -> result.put("nids", a.get("nids.count", 0)));

		return result;
	}

	private void addPeerToMap(Peer peer, Map<String, Set<Peer>> pbt) {
		peer.supportedTransports()
			.forEachOrdered(ti -> pbt.computeIfAbsent(ti.name(), k -> new HashSet<>()).add(peer));
	}

	public List<JSONObject> getLivePeers() {
		return this.addressBook.recentPeers()
			.map(peer -> {
				return serialization.toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getLiveNIDS() {
		JSONArray NIDS = new JSONArray();
		this.addressBook.recentPeers().forEachOrdered(peer -> NIDS.put(peer.getNID().toString()));
		return new JSONObject().put("nids", NIDS);
	}

	public List<JSONObject> getPeers() {
		return this.addressBook.peers()
			.map(peer -> {
				return serialization.toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getPeer(String id) throws JSONException {
		JSONObject result = new JSONObject();

		try {
			EUID euid = EUID.valueOf(id);
			Peer peer = this.addressBook.peer(euid);
			if (peer != null) {
				return serialization.toJsonObject(peer, Output.API);
			}
		} catch (NumberFormatException ex) {
			// Ignore, return empty object
		}

		return result;
	}

}
