package org.radix.api.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.radixdlt.common.EUID;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.Protocol;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerHandler.PeerDomain;
import org.radix.network.peers.PeerStore;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.routing.RoutingHandler;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.state.State;
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

		JSONArray tcp = new JSONArray();
		for (Peer peer : Network.getInstance().get(Protocol.TCP, State.CONNECTED)) {
			tcp.put(Modules.get(Serialization.class).toJsonObject(peer, Output.API));
		}
		JSONArray udp = new JSONArray();
		for (Peer peer : Network.getInstance().get(Protocol.UDP, State.CONNECTED)) {
			udp.put(Modules.get(Serialization.class).toJsonObject(peer, Output.API));
		}
		result.put("tcp", tcp);
		result.put("udp", udp);
		Modules.ifAvailable(SystemMetaData.class, a -> result.put("nids", a.get("nids.count", 0)));

		return result;
	}

	public List<JSONObject> getLivePeers() throws IOException {
		return Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, (PeerFilter) null, null).stream()
			.map(peer -> {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getLiveNIDS() throws IOException {
		JSONArray NIDS = new JSONArray();
		Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK).forEach(peer -> NIDS.put(peer.getSystem().getNID().toString()));
		return new JSONObject().put("nids", NIDS);
	}

	public JSONObject getLiveNIDS(String planck) throws IOException {
		List<EUID> NIDS = Modules.get(RoutingHandler.class).getNIDS(Integer.valueOf(planck));
		Collections.sort(NIDS);

		JSONObject json = new JSONObject().put("nids", new JSONArray());
		for (EUID NID : NIDS)
			json.getJSONArray("nids").put(NID.toString());

		return json;
	}

	public List<JSONObject> getPeers() throws IOException {
		return Modules.get(PeerHandler.class).getPeers(PeerDomain.PERSISTED, (PeerFilter) null, null).stream()
			.map(peer -> {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.WIRE);
			})
			.collect(Collectors.toList());
	}

	public JSONObject getPeer(String id) throws IOException, JSONException, URISyntaxException {
		JSONObject result = new JSONObject();

		try {
			EUID euid = EUID.valueOf(id);
			Peer peer = Modules.get(PeerStore.class).getPeer(euid);
			if (peer != null) {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.API);
			}
		} catch (NumberFormatException ex) {
			URI uri = new URI(Network.URI_PREFIX + id);
			Peer peer = Modules.get(PeerStore.class).getPeer(uri);
			if (peer != null) {
				return Modules.get(Serialization.class).toJsonObject(peer, Output.API);
			}
		}

		return result;
	}

}
