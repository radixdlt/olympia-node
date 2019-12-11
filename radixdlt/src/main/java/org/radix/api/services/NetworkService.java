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
	private NetworkService() {}

	private final static NetworkService instance = new NetworkService();

	public static NetworkService getInstance() {
		return instance;
	}

 	public JSONObject getSelf() {
 		JSONObject self = new JSONObject();
		self.put("system", Modules.get(Serialization.class).toJsonObject(LocalSystem.getInstance(), Output.WIRE));
 		return self;
 	}

 	public JSONObject getNetwork() throws JSONException {
		JSONObject result = new JSONObject();

		// sorted by transport name
		Map<String, Set<Peer>> peersByTransport = new TreeMap<>();

		Modules.get(AddressBook.class).recentPeers()
			.forEachOrdered(p -> addPeerToMap(p, peersByTransport));


		for (Map.Entry<String, Set<Peer>> e : peersByTransport.entrySet()) {
			JSONArray transportPeers = new JSONArray();
			for (Peer peer : e.getValue()) {
				transportPeers.put(Modules.get(Serialization.class).toJsonObject(peer, Output.API));
			}
			result.put(e.getKey(), transportPeers);
		}
		Modules.ifAvailable(SystemMetaData.class, a -> result.put("nids", a.get("nids.count", 0)));

		return result;
	}

	private void addPeerToMap(Peer peer, Map<String, Set<Peer>> pbt) {
		peer.supportedTransports()
			.forEachOrdered(ti -> pbt.computeIfAbsent(ti.name(), k -> new HashSet<>()).add(peer));
	}

	public List<JSONObject> getLivePeers() {
		return Modules.get(AddressBook.class).recentPeers()
			.map(peer -> {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getLiveNIDS() {
		JSONArray NIDS = new JSONArray();
		Modules.get(AddressBook.class).recentPeers().forEachOrdered(peer -> NIDS.put(peer.getNID().toString()));
		return new JSONObject().put("nids", NIDS);
	}

	public List<JSONObject> getPeers() {
		return Modules.get(AddressBook.class).peers()
			.map(peer -> {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getPeer(String id) throws JSONException {
		JSONObject result = new JSONObject();

		try {
			EUID euid = EUID.valueOf(id);
			Peer peer = Modules.get(AddressBook.class).peer(euid);
			if (peer != null) {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.API);
			}
		} catch (NumberFormatException ex) {
			// Ignore, return empty object
		}

		return result;
	}

}
