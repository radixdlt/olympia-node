/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.service;

import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.addressbook.AddressBookEntry;
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
	private final AddressBook addressBook;
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
		AddressBook addressBook,
		P2PConfig p2PConfig,
		Addressing addressing
	) {
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.systemCounters = systemCounters;
		this.endpointStatuses = endpointStatuses;
		this.peersView = peersView;
		this.addressBook = addressBook;
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
		var peerArray = jsonArray();

		peersView.peers()
			.map(this::peerToJson)
			.forEach(peerArray::put);

		return peerArray;
	}

	public JSONArray getNetworkingAddressBook() {
		final var entriesArray = jsonArray();
		addressBook.knownPeers().values().forEach(v -> entriesArray.put(addressBookEntryToJson(v)));
		return entriesArray;
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

	private JSONObject addressBookEntryToJson(AddressBookEntry e) {
		final var knownAddressesArray = jsonArray();

		e.getKnownAddresses().forEach(addr -> {
			final var addrObj = jsonObject().put("uri", addr.getUri());
			addr.getLastSuccessfulConnection().ifPresent(ts -> addrObj.put("lastSuccessfulConnection", ts));
			knownAddressesArray.put(addrObj);
		});

		final var entryObj = jsonObject()
			.put("address", addressing.forNodes().of(e.getNodeId().getPublicKey()))
			.put("banned", e.isBanned())
			.put("knownAddresses", knownAddressesArray);

		e.bannedUntil().ifPresent(bannedUntil -> entryObj.put("bannedUntil", bannedUntil));

		return entryObj;
	}
}

