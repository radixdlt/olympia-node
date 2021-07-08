/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.service;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.EndpointStatus;
import com.radixdlt.api.qualifier.Endpoints;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.utils.Bytes;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.radixdlt.api.JsonRpcUtil.ARRAY;
import static com.radixdlt.api.JsonRpcUtil.fromList;
import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class SystemConfigService {
	@VisibleForTesting
	static final List<CounterType> API_COUNTERS = List.of(
		CounterType.COUNT_APIDB_QUEUE_SIZE,
		CounterType.COUNT_APIDB_FLUSH_COUNT,
		CounterType.COUNT_APIDB_BALANCE_TOTAL,
		CounterType.COUNT_APIDB_BALANCE_READ,
		CounterType.COUNT_APIDB_BALANCE_WRITE,
		CounterType.COUNT_APIDB_BALANCE_BYTES_READ,
		CounterType.COUNT_APIDB_BALANCE_BYTES_WRITE,
		CounterType.COUNT_APIDB_TOKEN_TOTAL,
		CounterType.COUNT_APIDB_TOKEN_READ,
		CounterType.COUNT_APIDB_TOKEN_WRITE,
		CounterType.COUNT_APIDB_TOKEN_BYTES_READ,
		CounterType.COUNT_APIDB_TOKEN_BYTES_WRITE,
		CounterType.COUNT_APIDB_TRANSACTION_TOTAL,
		CounterType.COUNT_APIDB_TRANSACTION_READ,
		CounterType.COUNT_APIDB_TRANSACTION_WRITE,
		CounterType.COUNT_APIDB_TRANSACTION_BYTES_READ,
		CounterType.COUNT_APIDB_TRANSACTION_BYTES_WRITE,
		CounterType.ELAPSED_APIDB_BALANCE_READ,
		CounterType.ELAPSED_APIDB_BALANCE_WRITE,
		CounterType.ELAPSED_APIDB_TOKEN_READ,
		CounterType.ELAPSED_APIDB_TOKEN_WRITE,
		CounterType.ELAPSED_APIDB_TRANSACTION_READ,
		CounterType.ELAPSED_APIDB_TRANSACTION_WRITE,
		CounterType.ELAPSED_APIDB_FLUSH_TIME
	);

	@VisibleForTesting
	static final List<CounterType> BFT_COUNTERS = List.of(
		CounterType.BFT_CONSENSUS_EVENTS,
		CounterType.BFT_INDIRECT_PARENT,
		CounterType.BFT_PROCESSED,
		CounterType.BFT_PROPOSALS_MADE,
		CounterType.BFT_REJECTED,
		CounterType.BFT_TIMEOUT,
		CounterType.BFT_TIMED_OUT_VIEWS,
		CounterType.BFT_TIMEOUT_QUORUMS,
		CounterType.BFT_STATE_VERSION,
		CounterType.BFT_VERTEX_STORE_SIZE,
		CounterType.BFT_VERTEX_STORE_FORKS,
		CounterType.BFT_VERTEX_STORE_REBUILDS,
		CounterType.BFT_VOTE_QUORUMS,
		CounterType.BFT_SYNC_REQUESTS_SENT,
		CounterType.BFT_SYNC_REQUEST_TIMEOUTS
	);

	@VisibleForTesting
	static final List<CounterType> MEMPOOL_COUNTERS = List.of(
		CounterType.MEMPOOL_COUNT,
		CounterType.MEMPOOL_MAXCOUNT,
		CounterType.MEMPOOL_RELAYER_SENT_COUNT,
		CounterType.MEMPOOL_ADD_SUCCESS,
		CounterType.MEMPOOL_PROPOSED_TRANSACTION,
		CounterType.MEMPOOL_ERRORS_HOOK,
		CounterType.MEMPOOL_ERRORS_CONFLICT,
		CounterType.MEMPOOL_ERRORS_OTHER
	);

	@VisibleForTesting
	static final List<CounterType> RADIX_ENGINE_COUNTERS = List.of(
		CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS,
		CounterType.RADIX_ENGINE_USER_TRANSACTIONS,
		CounterType.RADIX_ENGINE_SYSTEM_TRANSACTIONS
	);

	@VisibleForTesting
	static final List<CounterType> SYNC_COUNTERS = List.of(
		CounterType.SYNC_LAST_READ_MILLIS,
		CounterType.SYNC_INVALID_COMMANDS_RECEIVED,
		CounterType.SYNC_PROCESSED,
		CounterType.SYNC_TARGET_STATE_VERSION,
		CounterType.SYNC_TARGET_CURRENT_DIFF,
		CounterType.SYNC_REMOTE_REQUESTS_PROCESSED
	);

	@VisibleForTesting
	static final List<CounterType> NETWORKING_COUNTERS = List.of(
		CounterType.MESSAGES_INBOUND_RECEIVED,
		CounterType.MESSAGES_INBOUND_PROCESSED,
		CounterType.MESSAGES_INBOUND_DISCARDED,
		CounterType.MESSAGES_OUTBOUND_ABORTED,
		CounterType.MESSAGES_OUTBOUND_PENDING,
		CounterType.MESSAGES_OUTBOUND_PROCESSED,
		CounterType.MESSAGES_OUTBOUND_SENT,
		CounterType.NETWORKING_UDP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_DROPPED_MESSAGES,
		CounterType.NETWORKING_TCP_IN_OPENED,
		CounterType.NETWORKING_TCP_OUT_OPENED,
		CounterType.NETWORKING_TCP_CLOSED,
		CounterType.NETWORKING_SENT_BYTES,
		CounterType.NETWORKING_RECEIVED_BYTES
	);

	private final JSONObject radixEngineConfiguration;
	private final JSONObject mempoolConfiguration;
	private final JSONObject apiConfiguration;
	private final JSONObject bftConfiguration;
	private final JSONObject syncConfiguration;
	private final JSONObject checkpointsConfiguration;
	private final JSONObject networkingConfiguration;

	private final InMemorySystemInfo inMemorySystemInfo;
	private final SystemCounters systemCounters;
	private final List<EndpointStatus> endpointStatuses;
	private final PeersView peersView;
	private final Addressing addressing;

	@Inject
	public SystemConfigService(
		@Endpoints List<EndpointStatus> endpointStatuses,
		@PacemakerTimeout long pacemakerTimeout,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		@MempoolMaxSize int mempoolMaxSize,
		@MempoolThrottleMs long mempoolThrottleMs,
		@Genesis VerifiedTxnsAndProof genesis,
		TreeMap<Long, ForkConfig> forks,
		SyncConfig syncConfig,
		InMemorySystemInfo inMemorySystemInfo,
		SystemCounters systemCounters,
		PeersView peersView,
		P2PConfig p2PConfig,
		Addressing addressing
	) {
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.systemCounters = systemCounters;
		this.endpointStatuses = endpointStatuses;
		this.peersView = peersView;
		this.addressing = addressing;

		radixEngineConfiguration = prepareRadixEngineConfiguration(forks);
		mempoolConfiguration = prepareMempoolConfiguration(mempoolMaxSize, mempoolThrottleMs);
		apiConfiguration = prepareApiConfiguration(endpointStatuses);
		bftConfiguration = prepareBftConfiguration(pacemakerTimeout, bftSyncPatienceMillis);
		syncConfiguration = syncConfig.asJson();
		checkpointsConfiguration = prepareCheckpointsConfiguration(genesis);
		networkingConfiguration = prepareNetworkingConfiguration(p2PConfig);
	}

	public JSONObject getApiConfiguration() {
		return apiConfiguration;
	}

	public JSONObject getApiData() {
		return countersToJson(systemCounters, API_COUNTERS, false);
	}

	public JSONObject getBftConfiguration() {
		return bftConfiguration;
	}

	public JSONObject getBftData() {
		return countersToJson(systemCounters, BFT_COUNTERS, true);
	}

	public JSONObject getMempoolConfiguration() {
		return mempoolConfiguration;
	}

	public JSONObject getMempoolData() {
		return countersToJson(systemCounters, MEMPOOL_COUNTERS, true);
	}

	public JSONObject getLatestProof() {
		var proof = inMemorySystemInfo.getCurrentProof();
		return proof == null ? new JSONObject() : proof.asJSON(addressing);
	}

	public JSONObject getLatestEpochProof() {
		var proof = inMemorySystemInfo.getEpochProof();
		return proof == null ? new JSONObject() : proof.asJSON(addressing);
	}

	public JSONObject getRadixEngineConfiguration() {
		return radixEngineConfiguration;
	}

	public JSONObject getRadixEngineData() {
		return countersToJson(systemCounters, RADIX_ENGINE_COUNTERS, true);
	}

	public JSONObject getSyncConfig() {
		return syncConfiguration;
	}

	public JSONObject getSyncData() {
		return countersToJson(systemCounters, SYNC_COUNTERS, true);
	}

	public JSONObject getNetworkingConfiguration() {
		return networkingConfiguration;
	}

	public JSONArray getNetworkingPeers() {
		var peerArray = new JSONArray();

		peersView.peers()
			.map(this::peerToJson)
			.forEach(peerArray::put);

		return peerArray;
	}

	public long getNetworkingPeersCount() {
		return peersView.peers().count();
	}

	public JSONObject getNetworkingData() {
		return countersToJson(systemCounters, NETWORKING_COUNTERS, false);
	}

	public JSONObject getCheckpoints() {
		return checkpointsConfiguration;
	}

	public void withEndpointStatuses(Consumer<? super EndpointStatus> consumer) {
		endpointStatuses.forEach(consumer);
	}

	public AccumulatorState accumulatorState() {
		return inMemorySystemInfo.getCurrentProof().getAccumulatorState();
	}

	@VisibleForTesting
	static JSONObject countersToJson(SystemCounters counters, List<CounterType> types, boolean skipTopLevel) {
		var result = jsonObject();
		types.forEach(counterType -> counterToJson(result, counters, counterType, skipTopLevel));
		return result;
	}

	@VisibleForTesting
	static void counterToJson(JSONObject obj, SystemCounters systemCounters, CounterType type, boolean skipTopLevel) {
		var ptr = obj;
		var iterator = List.of(type.jsonPath().split("\\.")).listIterator();

		if (skipTopLevel && iterator.hasNext()) {
			iterator.next();
		}

		while (iterator.hasNext()) {
			var element = toCamelCase(iterator.next());

			if (ptr.has(element)) {
				ptr = ptr.getJSONObject(element);
			} else {
				if (iterator.hasNext()) {
					var newObj = jsonObject();
					ptr.put(element, newObj);
					ptr = newObj;
				} else {
					ptr.put(element, systemCounters.get(type));
				}
			}
		}
	}

	@VisibleForTesting
	static String toCamelCase(String input) {
		var output = new StringBuilder();

		boolean upCaseNext = false;

		for (var chr : input.toCharArray()) {
			if (chr == '_') {
				upCaseNext = true;
				continue;
			}

			output.append(upCaseNext ? Character.toUpperCase(chr) : chr);
			upCaseNext = false;
		}

		return output.toString();
	}

	@VisibleForTesting
	static JSONObject prepareApiConfiguration(List<EndpointStatus> statuses) {
		var enabled = statuses.stream()
			.filter(EndpointStatus::enabled)
			.map(EndpointStatus::name)
			.collect(Collectors.toList());

		return jsonObject().put(
			"endpoints",
			fromList(enabled, endpoint -> "/" + endpoint)
		);
	}

	@VisibleForTesting
	static JSONObject prepareRadixEngineConfiguration(TreeMap<Long, ForkConfig> forksConfigs) {
		var forks = jsonArray();

		forksConfigs.forEach((e, config) -> forks.put(
			jsonObject()
				.put("name", config.getName())
				.put("version", config.getVersion().name().toLowerCase())
				.put("maxRounds", config.getConfig().getMaxRounds())
				.put("maxSigsPerRound", config.getConfig().getMaxSigsPerRound().orElse(-1))
				.put("maxValidators", config.getConfig().getMaxValidators())
				.put("epoch", e)
		));

		return jsonObject().put(ARRAY, forks);
	}

	@VisibleForTesting
	static JSONObject prepareMempoolConfiguration(int mempoolMaxSize, long mempoolThrottleMs) {
		return jsonObject()
			.put("maxSize", mempoolMaxSize)
			.put("throttleMs", mempoolThrottleMs);
	}

	@VisibleForTesting
	static JSONObject prepareBftConfiguration(long pacemakerTimeout, long bftSyncPatienceMillis) {
		return jsonObject()
			.put("pacemakerTimeout", pacemakerTimeout)
			.put("bftSyncPatienceMs", bftSyncPatienceMillis);
	}

	@VisibleForTesting
	JSONObject prepareCheckpointsConfiguration(VerifiedTxnsAndProof genesis) {
		return jsonObject()
			.put("txn", fromList(genesis.getTxns(), txn -> Bytes.toHexString(txn.getPayload())))
			.put("proof", genesis.getProof().asJSON(addressing));
	}

	private JSONObject prepareNetworkingConfiguration(P2PConfig p2PConfig) {
		return jsonObject()
			.put("defaultPort", p2PConfig.defaultPort())
			.put("discoveryInterval", p2PConfig.discoveryInterval())
			.put("listenAddress", p2PConfig.listenAddress())
			.put("listenPort", p2PConfig.listenPort())
			.put("broadcastPort", p2PConfig.broadcastPort())
			.put("peerConnectionTimeout", p2PConfig.peerConnectionTimeout())
			.put("maxInboundChannels", p2PConfig.maxInboundChannels())
			.put("maxOutboundChannels", p2PConfig.maxOutboundChannels())
			.put("channelBufferSize", p2PConfig.channelBufferSize())
			.put("peerLivenessCheckInterval", p2PConfig.peerLivenessCheckInterval())
			.put("pingTimeout", p2PConfig.pingTimeout())
			.put("seedNodes", fromList(p2PConfig.seedNodes(), seedNode -> seedNode));
	}

	private JSONObject peerToJson(PeersView.PeerInfo peer) {
		var channelsJson = jsonArray();
		var peerJson = jsonObject().put("address", addressing.forNodes().of(peer.getNodeId().getPublicKey()));

		peer.getChannels().forEach(channel -> {
			var channelJson = jsonObject()
				.put("type", channel.isOutbound() ? "out" : "in")
				.put("localPort", channel.getSocketAddress().getPort())
				.put("ip", channel.getSocketAddress().getAddress().getHostAddress());

			channel.getUri().ifPresent(uri -> channelJson.put("uri", uri.toString()));
			channelsJson.put(channelJson);
		});
		peerJson.put("channels", channelsJson);
		return peerJson;
	}
}

