package org.radix.api.services;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;

import org.radix.database.exceptions.DatabaseException;
import org.radix.mass.NodeMass;
import org.radix.mass.NodeMassStore;
import org.radix.modules.Modules;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerHandler.PeerDomain;
import org.radix.network.peers.filters.PeerFilter;
import org.radix.routing.NodeAddressGroupTable;
import org.radix.routing.RoutingHandler;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

public class GraphService
{
	private static final GraphService GRAPH_SERVICE = new GraphService();

	public static GraphService getInstance() {
		return GRAPH_SERVICE;
	}

	private GraphService() {}

	public List<JSONObject> getNodeMasses(String timestamp) throws DatabaseException {
		long ts = Long.parseLong(timestamp);
		long tsms = ts < Integer.MAX_VALUE ? ts * 1000L : ts;
		int planck = Modules.get(Universe.class).toPlanck(tsms, Offset.NONE);
		return Modules.get(NodeMassStore.class).getNodeMasses(planck).stream()
				.map(nodeMass -> Modules.get(Serialization.class).toJsonObject(nodeMass, Output.API))
				.collect(Collectors.toList());
	}

	public List<JSONObject> getLivePeers() throws IOException {
		return Modules.get(PeerHandler.class).getPeers(PeerDomain.NETWORK, (PeerFilter) null, null).stream()
			.map(peer -> Modules.get(Serialization.class).toJsonObject(peer, Output.WIRE))
			.collect(Collectors.toList());
	}


	public JSONObject getNodeMass(String nid, String timestamp) {
		long ts = Long.parseLong(timestamp);
		long tsms = ts < Integer.MAX_VALUE ? ts * 1000L : ts;
		int planck = Modules.get(Universe.class).toPlanck(tsms, Offset.NONE);
		NodeMass nodeMass = Modules.get(NodeMassStore.class).getNodeMass(planck, EUID.valueOf(nid));
		return Modules.get(Serialization.class).toJsonObject(nodeMass, Output.API);
	}

	public JSONObject getRoutingTable(String nid, String timestamp) throws DatabaseException {
		JSONObject json = new JSONObject();

		long ts = Long.parseLong(timestamp);
		long tsms = ts < Integer.MAX_VALUE ? ts * 1000L : ts;
		int planck = Modules.get(Universe.class).toPlanck(tsms, Offset.NONE);
		NodeAddressGroupTable groupTable = Modules.get(RoutingHandler.class).getNodeAddressGroupTable(EUID.valueOf(nid), planck);

		for (int group : groupTable.getGroups())
		{
			List<EUID> groupNIDS = groupTable.getGroup(group);
			JSONArray groupArray = new JSONArray();
			for (EUID groupNID : groupNIDS)
				groupArray.put(groupNID.toString());
			json.put(String.valueOf(group), groupArray);
		}

		return json;
	}
}
