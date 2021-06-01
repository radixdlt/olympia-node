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
import org.radix.universe.system.LocalSystem;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.network.transport.TransportInfo;
import com.radixdlt.statecomputer.MaxTxnsPerProposal;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.systeminfo.InMemorySystemInfo;

import java.util.TreeMap;
import java.util.stream.Stream;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class SystemConfigService {
	private final JSONObject radixEngineConfiguration;
	private final JSONObject mempoolConfiguration;

	private final InMemorySystemInfo inMemorySystemInfo;
	private final AddressBook addressBook;
	private final PeerWithSystem localPeer;

	@Inject
	public SystemConfigService(
		@PacemakerTimeout long pacemakerTimeout,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		@MempoolMaxSize int mempoolMaxSize,
		@MempoolThrottleMs long mempoolThrottleMs,
		@MinValidators int minValidators,
		@MaxValidators int maxValidators,
		@MaxTxnsPerProposal int maxTxnsPerProposal,
		TreeMap<Long, ForkConfig> forkConfigTreeMap,
		LocalSystem localSystem,
		@Genesis VerifiedTxnsAndProof genesis,
		InMemorySystemInfo inMemorySystemInfo,
		AddressBook addressBook,
		PeerWithSystem localPeer
	) {
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.addressBook = addressBook;
		this.localPeer = localPeer;

		this.radixEngineConfiguration = prepareRadixEngineConfiguration(forkConfigTreeMap, minValidators, maxValidators, maxTxnsPerProposal);
		this.mempoolConfiguration = prepareMempoolConfiguration(mempoolMaxSize, mempoolThrottleMs);
	}

	public JSONObject getRadixEngineConfiguration() {
		return radixEngineConfiguration;
	}

	public JSONObject getMempoolConfiguration() {
		return mempoolConfiguration;
	}

	private static JSONObject prepareRadixEngineConfiguration(
		TreeMap<Long, ForkConfig> forkConfigTreeMap,
		int minValidators,
		int maxValidators,
		int maxTxnsPerProposal
	) {
		var forks = jsonArray();
		forkConfigTreeMap.forEach((e, config) -> forks.put(
			jsonObject()
				.put("name", config.getName())
				.put("ceiling_view", config.getEpochCeilingView().number())
				.put("epoch", e)
		));

		return jsonObject().put("radix_engine", jsonObject()
			.put("min_validators", minValidators)
			.put("max_validators", maxValidators)
			.put("max_txns_per_proposal", maxTxnsPerProposal)
			.put("forks", forks)
		);
	}

	private static JSONObject prepareMempoolConfiguration(int mempoolMaxSize, long mempoolThrottleMs) {
		return jsonObject().put("mempool", jsonObject()
			.put("max_size", mempoolMaxSize)
			.put("throttle_ms", mempoolThrottleMs)
		);
	}

	public JSONObject getApiConfiguration() {
		//TODO: implement it
		return null;
	}

	public JSONArray getLivePeers() {
		var peerArray = new JSONArray();

		selfAndOthers(addressBook.recentPeers())
			.map(this::peerToJson)
			.forEach(peerArray::put);

		return peerArray;
	}

	private JSONObject peerToJson(PeerWithSystem peer) {
		var json = jsonObject().put("address", ValidatorAddress.of(peer.getSystem().getKey()));

		peer.getSystem()
			.supportedTransports()
			.filter(this::isTCP)
			.forEach(t -> json.put("endpoint", getHostAndPort(t)));

		return json;
	}

	private String getHostAndPort(TransportInfo t) {
		return t.metadata().get("host") + ":" + t.metadata().get("port");
	}

	private boolean isTCP(TransportInfo t) {
		return t.name().equals("TCP");
	}

	//TODO: remove code below

//	void respondWithCurrentProof(final HttpServerExchange exchange) {
//		var proof = inMemorySystemInfo.getCurrentProof();
//		respond(exchange, proof == null ? new JSONObject() : proof.asJSON());
//	}
//
//	void respondWithEpochProof(final HttpServerExchange exchange) {
//		var proof = inMemorySystemInfo.getEpochProof();
//		respond(exchange, proof == null ? new JSONObject() : proof.asJSON());
//	}
//
//	@VisibleForTesting
//	void respondWithLocalSystem(final HttpServerExchange exchange) {
//		var json = DefaultSerialization.getInstance().toJsonObject(localSystem, DsonOutput.Output.API);
//		respond(exchange, json);
//	}
//
//	@VisibleForTesting
//	void respondWithGenesis(final HttpServerExchange exchange) {
//		var jsonObject = new JSONObject();
//		var txns = new JSONArray();
//		genesis.getTxns().forEach(txn -> txns.put(Bytes.toHexString(txn.getPayload())));
//		jsonObject.put("txn", txns.get(0));
//		jsonObject.put("proof", genesis.getProof().asJSON());
//
//		respond(exchange, jsonObject);
//	}


	private Stream<PeerWithSystem> selfAndOthers(Stream<PeerWithSystem> others) {
		return Stream.concat(Stream.of(this.localPeer), others).distinct();
	}

	//TODO: refactor into pieces of /system methods
//	void handleConfig(HttpServerExchange exchange) {
//		respond(exchange, jsonObject()
//			.put("consensus", jsonObject()
//				.put("pacemaker_timeout", pacemakerTimeout)
//				.put("bft_sync_patience_ms", bftSyncPatienceMillis)
//			)
//		);
//	}

}
