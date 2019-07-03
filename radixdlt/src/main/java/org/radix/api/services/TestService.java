package org.radix.api.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Objects;

import org.json.JSONObject;
import org.radix.Radix;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Network;
import org.radix.network.messages.PeersMessage;
import org.radix.network.messaging.Messaging;
import org.radix.network.messaging.Message.Direction;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import org.radix.universe.system.RadixSystem;
import com.radixdlt.utils.Bytes;

import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.serialization.Serialization;

/**
 * Services used by test endpoints.
 */
public final class TestService {
	private static final Logger log = Logging.getLogger();

	private static final String SHARD_JSON_TEMPLATE =
			"{\n" +
			"  \"anchor\": %s,\n" +
			"  \"serializer\": \"radix.shard.space\",\n" +
			"  \"range\": {\n" +
			"    \"high\": %s,\n" +
			"    \"low\": %s,\n" +
			"    \"serializer\": \"radix.shards.range\"\n" +
			"  }\n" +
			"}";

	static final class Holder {
		static final TestService TEST_SERVICE = new TestService();
	}

	/**
	 * Returns the singleton instance, creating the object on first call.
	 *
	 * @return The singleton instance
	 */
	public static TestService getInstance() {
		return Holder.TEST_SERVICE;
	}

	private TestService() {
		// Nothing to do here
	}

	/**
	 * Inject a {@link PeerMessage} into the inbound messaging queue based on
	 * the specified parameters.
	 *
	 * @param key The public key of the node, in hexadecimal
	 * @param anchor The anchor shard for the node, in decimal
	 * @param high The high shard range, in decimal
	 * @param low The low shard range, in decimal
	 * @param ipaddr The IPv4 address of the host as a dotted quad
	 * @param port The port for the host as a decimal
	 * @return A JSON object specifying the NID of the newly created host in the format:
	 *     <code>{ "nid": "<8 hexadecimal digits>" }</code>
	 */
	public String newPeer(String key, String anchor, String high, String low, String ipaddr, String port) {
		try {
			Objects.requireNonNull("key is required", key);
			Objects.requireNonNull("anchor is required", anchor);
			Objects.requireNonNull("high is required", high);
			Objects.requireNonNull("low is required", low);
			Objects.requireNonNull("ip is required", ipaddr);
			Objects.requireNonNull("port is required", port);
			ECKeyPair keyValue = new ECKeyPair(Bytes.fromHexString(key));
			int portValue = Integer.parseInt(port);

			// Some special magic to avoid constructor range checks.
			String json = String.format(SHARD_JSON_TEMPLATE, anchor, high, low);
			ShardSpace shards = Modules.get(Serialization.class).fromJson(json, ShardSpace.class);

			RadixSystem system = new RadixSystem(
				keyValue.getPublicKey(), Radix.AGENT, Radix.AGENT_VERSION, Radix.PROTOCOL_VERSION, shards, portValue);
			Peer peer = new Peer(Network.getURI(ipaddr, portValue));
			peer.setSystem(system);
			PeersMessage peersMessage = new PeersMessage();
			peersMessage.setPeers(Collections.singletonList(peer));
			peersMessage.setDirection(Direction.INBOUND);
			peersMessage.setTimestamp(Timestamps.RECEIVED, Modules.get(NtpService.class).getUTCTimeMS());
			peersMessage.setTimestamp(Timestamps.LATENCY, java.lang.System.nanoTime());
			Messaging.getInstance().received(peersMessage, peer);
			log.debug("Submitted peers message for NID " + keyValue.getUID().toString());
			JSONObject result = new JSONObject();
			result.put("nid", keyValue.getUID().toString());
			return result.toString();
		} catch (CryptoException e) {
			log.error("While adding new peer", e);
			throw new IllegalArgumentException("Invalid key specified: " + key);
		} catch (IOException e) {
			log.error("While adding new peer", e);
			throw new UncheckedIOException(e);
		}
	}
}
