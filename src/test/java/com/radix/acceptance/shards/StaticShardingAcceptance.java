package com.radix.acceptance.shards;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.radix.utils.primitives.Bytes;

import com.google.common.io.CharStreams;
import com.radixdlt.client.application.identity.RadixIdentities;
import com.radixdlt.client.application.identity.RadixIdentity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

	private static final String PRIMARY_HOST = "http://localhost:8080";
	private static final String OTHER_HOST   = "http://localhost:8081";

	@Test
	public void broadcast_of_shard_range__scenario_1() {
		// Scenario 1:
		// Given ‌that I'm running a live node,
		// When ‌join the network / periodically,
		// Then ‌I want to include my shard range on peer discovery messages.

		// This test indirectly does what is written above, by showing that shards
		// *are* passed between nodes via the API.
        checkPeersHaveShards(getNonEmptyPeersFrom(PRIMARY_HOST));
        checkPeersHaveShards(getNonEmptyPeersFrom(OTHER_HOST));
	}

	@Test
	public void receival_of_shard_range__scenario_1() throws InterruptedException {
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
		JSONArray peers = getNonEmptyPeersFrom(PRIMARY_HOST);
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
	public void receival_of_shard_range__scenario_3() throws InterruptedException {
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

		String systemString = getURL("http://localhost:8081/api/system");
		JSONObject system = new JSONObject(systemString);
		String key = Bytes.toHexString(fromBase64(system.getString("key")));
		JSONObject shards = system.getJSONObject("shards");
		long anchor = shards.getLong("anchor");
		JSONObject range = shards.getJSONObject("range");
		long high = range.getLong("high");
		long low = range.getLong("low");

		String nid = newPeer(key, anchor, high, low, ip);
		TimeUnit.SECONDS.sleep(2); // Allow propagation of message in node
		JSONArray peers = getNonEmptyPeersFrom(PRIMARY_HOST);
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

    private String newPeer(String key, long anchor, long high, long low, String ip) {
		String query = String.format(
			"http://localhost:8080/api/test/newpeer?key=%s&anchor=%s&high=%s&low=%s&ip=%s",
			key, anchor, high, low, ip);
		String result = getURL(query);
		JSONObject nidResult = new JSONObject(result);
		assertTrue("result has no NID", nidResult.has("nid"));
		return ":uid:" + nidResult.getString("nid");
    }

	private JSONArray getNonEmptyPeersFrom(String urlPrefix) {
        String result = getURL(urlPrefix + "/api/network/peers");
        JSONArray json = new JSONArray(result);
        assertFalse("Peers list is empty", json.isEmpty());
        return json;
	}

	public String getURL(String url) {
        try {
            URL requestUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Charset", "utf-8");
            try (InputStream inputStream = connection.getInputStream()) {
                return CharStreams.toString(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
