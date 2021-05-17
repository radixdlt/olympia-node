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

package com.radixdlt.api.controller;

import com.google.inject.Inject;
import com.radixdlt.api.Controller;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.statecomputer.MaxTxnsPerProposal;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.forks.ForkConfig;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.TreeMap;

import static com.radixdlt.api.RestUtils.respond;

//TODO: merge functionality into SystemService
public class ConfigController implements Controller {
	private final long pacemakerTimeout;
	private final int bftSyncPatienceMillis;
	private final long mempoolMaxSize;
	private final long mempoolThrottleMs;
	private final int minValidators;
	private final int maxValidators;
	private final int maxTxnsPerProposal;
	private final TreeMap<Long, ForkConfig> forkConfigTreeMap;

	@Inject
	public ConfigController(
		@PacemakerTimeout long pacemakerTimeout,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		@MempoolMaxSize int mempoolMaxSize,
		@MempoolThrottleMs long mempoolThrottleMs,
		@MinValidators int minValidators,
		@MaxValidators int maxValidators,
		@MaxTxnsPerProposal int maxTxnsPerProposal,
		TreeMap<Long, ForkConfig> forkConfigTreeMap
	) {
		this.pacemakerTimeout = pacemakerTimeout;
		this.bftSyncPatienceMillis = bftSyncPatienceMillis;
		this.mempoolMaxSize = mempoolMaxSize;
		this.mempoolThrottleMs = mempoolThrottleMs;
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
		this.maxTxnsPerProposal = maxTxnsPerProposal;
		this.forkConfigTreeMap = forkConfigTreeMap;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.get("/system/config", this::handleConfig);
	}

	void handleConfig(HttpServerExchange exchange) {
		var forks = new JSONArray();
		forkConfigTreeMap.forEach((e, config) -> forks.put(
			new JSONObject()
				.put("name", config.getName())
				.put("ceiling_view", config.getEpochCeilingView().number())
				.put("epoch", e)
		));

		respond(exchange, new JSONObject()
			.put("consensus", new JSONObject()
				.put("pacemaker_timeout", pacemakerTimeout)
				.put("bft_sync_patience_ms", bftSyncPatienceMillis)
			)
			.put("mempool", new JSONObject()
				.put("max_size", mempoolMaxSize)
				.put("throttle_ms", mempoolThrottleMs)
			)
			.put("radix_engine", new JSONObject()
				.put("min_validators", minValidators)
				.put("max_validators", maxValidators)
				.put("max_txns_per_proposal", maxTxnsPerProposal)
				.put("forks", forks)
			)
		);
	}
}
