package com.radix.acceptance.shards;

import com.radixdlt.client.application.RadixApplicationAPI;
import com.radixdlt.client.core.RadixEnv;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.radix.utils.primitives.Bytes;

import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assume.assumeTrue;

/**
 * Acceptance tests for Static Sharding RLAU-1100.
 * <p>
 * This covers cases:
 * <ul>
 *   <li>Broadcast of shard range: Scenario 1</li>
 *   <li>Receival of shard range: Scenario 1</li>
 *   <li>Receival of shard range: Scenario 3</li>
 * </ul>
 */
public class StaticShardingAcceptance {
	private static RadixApplicationAPI api;
	private static List<RadixNode> nodes;

	@BeforeClass
	public static void setUp() {
		RadixIdentity identity = RadixIdentities.createNew();
		api = RadixApplicationAPI.create(RadixEnv.getBootstrapConfig(), identity);
		api.discoverNodes();
		nodes = api.getNetworkState()
			.doOnNext(System.out::println)
			.flatMapIterable(RadixNetworkState::getNodes)
			.distinct()
			.filter(n -> {
				Call call = HttpClients.getSslAllTrustingClient().newCall(n.getHttpEndpoint("/api/atoms?uid=1234567890abcdef1234567890abcdef"));
				try (Response response = call.execute()) {
					return response.isSuccessful();
				} catch (Exception e) {
					return false;
				}
			})
			.take(5, TimeUnit.SECONDS)
			.toList()
			.blockingGet();
	}

	@Test
	public void broadcast_of_shard_range__scenario_1() throws Exception {
		assumeTrue(nodes.size() >= 2);
		// Scenario 1:
		// Given ‌that I'm running a live node,
		// When ‌join the network / periodically,
		// Then ‌I want to include my shard range on peer discovery messages.

		// This test indirectly does what is written above, by showing that shards
		// *are* passed between nodes via the API.
		checkPeersHaveShards(getNonEmptyPeersFrom(nodes.get(0)));
		checkPeersHaveShards(getNonEmptyPeersFrom(nodes.get(1)));
	}

	@Test
	public void receival_of_shard_range__scenario_1() throws Exception {
		assumeTrue(nodes.size() >= 2);

		// Scenario 1: Peers are sending valid shard ranges
		// Given ‌that I am running a node,
		// When ‌a peer sends me a shard range,
		// That is smaller than 2^44, and that is bigger than 0,
		// Then ‌I will continue talking to that node.
		RadixIdentity id = RadixIdentities.createNew();
		String key = Bytes.toHexString(id.getPublicKey().getPublicKey());
		long anchor = 0L;
		long high = 3;
		long low = -4;
		String ip = "172.18.0.4";

		String nid = newPeer(key, anchor, high, low, ip);
		TimeUnit.SECONDS.sleep(2); // Allow propagation of message in node
		JSONArray peers = getNonEmptyPeersFrom(nodes.get(0));
		boolean found = false;
		for (Object obj : peers) {
			assertTrue("object is not a JSONObject", obj instanceof JSONObject);
			JSONObject peer = (JSONObject) obj;
			JSONObject system = peer.getJSONObject("system");
			String peerNid = system.getString("nid");
			if (peerNid.equals(nid)) {
				found = true;
			}
		}
		assertTrue("New peer not found", found);
	}

	@Test
	public void receival_of_shard_range__scenario_3() throws Exception {
		assumeTrue(nodes.size() >= 2);

		/*
		 * Scenario 3: Peers are sending valid shard ranges that are correct.
		 * Given ‌that I am running a node,
		 * When ‌a peer sends me a shard range,
		 * That is consistent with what I know about that node in my own ledger.
		 * Then ‌I will continue talking to that node.
		 */

		// Here we will:
		// 1. Query the "other" node for it's key and shard config
		// 2. Inject a message with the same parameters in the primary node
		// 3. Observe that the primary node still recognises the "other" node
		String ip = "172.18.0.2";

		Call call = HttpClients.getSslAllTrustingClient().newCall(nodes.get(1).getHttpEndpoint("/api/system"));
		final String systemString;
		try (Response response = call.execute()) {
			systemString = response.body().string();
		}
		JSONObject system = new JSONObject(systemString);
		String key = Bytes.toHexString(fromBase64(system.getString("key")));
		JSONObject shards = system.getJSONObject("shards");
		long anchor = shards.getLong("anchor");
		JSONObject range = shards.getJSONObject("range");
		long high = range.getLong("high");
		long low = range.getLong("low");

		String nid = newPeer(key, anchor, high, low, ip);
		TimeUnit.SECONDS.sleep(2); // Allow propagation of message in node
		JSONArray peers = getNonEmptyPeersFrom(nodes.get(0));
		boolean found = false;
		for (Object obj : peers) {
			assertTrue("object is not a JSONObject", obj instanceof JSONObject);
			JSONObject peer = (JSONObject) obj;
			JSONObject newSystem = peer.getJSONObject("system");
			String peerNid = newSystem.getString("nid");
			if (peerNid.equals(nid)) {
				found = true;
			}
		}
		assertTrue("Peer was removed", found);
	}

	private byte[] fromBase64(String string) {
		if (!string.startsWith(":byt:")) {
			throw new IllegalArgumentException("Does not start with :byt: " + string);
		}
		return Bytes.fromBase64String(string.substring(5));
	}

	private void checkPeersHaveShards(JSONArray peers) {
		for (Object peerObject : peers) {
			JSONObject peer = (JSONObject) peerObject;
			assertTrue("Peer has no system object", peer.has("system"));
			JSONObject system = (JSONObject) peer.get("system");
			assertTrue("Peer's system has no shards object", system.has("shards"));
			JSONObject shards = (JSONObject) system.get("shards");
			assertTrue("Peer's shards has no anchor", shards.has("anchor"));
			assertTrue("Peer's shards has no range", shards.has("range"));
		}
	}

	private String newPeer(String key, long anchor, long high, long low, String ip) throws Exception {
		String query = String.format("/api/test/newpeer?key=%s&anchor=%s&high=%s&low=%s&ip=%s", key, anchor, high, low, ip);
		Call call = HttpClients.getSslAllTrustingClient().newCall(nodes.get(0).getHttpEndpoint(query));
		final String result;
		try (Response response = call.execute()) {
			result = response.body().string();
		}
		JSONObject nidResult = new JSONObject(result);
		assertTrue("result has no NID", nidResult.has("nid"));
		return ":uid:" + nidResult.getString("nid");
	}

	private JSONArray getNonEmptyPeersFrom(RadixNode node) throws Exception {
		Call call = HttpClients.getSslAllTrustingClient().newCall(node.getHttpEndpoint("/api/network/peers"));
		final String result;
		try (Response response = call.execute()) {
			result = response.body().string();
		}
		JSONArray json = new JSONArray(result);
		assertFalse("Peers list is empty", json.isEmpty());
		return json;
	}
}
