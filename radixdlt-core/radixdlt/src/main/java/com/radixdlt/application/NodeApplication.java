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

package com.radixdlt.application;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class NodeApplication {
	private static final Logger log = LogManager.getLogger();

	private final RadixAddress self;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final HashSigner hashSigner;
	private final EventDispatcher<MempoolAdd> mempoolAddEventDispatcher;
	private final Map<AID, NodeApplicationRequest> inflightRequests = new HashMap<>();

	@Inject
	public NodeApplication(
		@Self RadixAddress self,
		@Named("RadixEngine") HashSigner hashSigner,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher
	) {
		this.self = self;
		this.hashSigner = hashSigner;
		this.radixEngine = radixEngine;
		this.mempoolAddEventDispatcher = mempoolAddEventDispatcher;
	}

	private void processRequest(NodeApplicationRequest request) {
		log.info("NodeServiceRequest {}", request);

		try {
			// TODO: remove use of mempoolAdd message and add to mempool synchronously
			var txBuilder = radixEngine.construct(self, request.getActions());
			var txn = txBuilder.signAndBuild(hashSigner::sign);
			if (this.inflightRequests.containsKey(txn.getId())) {
				// TODO: use mempool to prevent double spending of substates so
				// TODO: that this occurs less frequently
				request.completableFuture()
					.ifPresent(c -> c.completeExceptionally(new RuntimeException("Transaction already in flight")));
				return;
			}
			this.inflightRequests.put(txn.getId(), request);
			this.mempoolAddEventDispatcher.dispatch(MempoolAdd.create(txn));
		} catch (TxBuilderException e) {
			log.error("Failed to fulfil request {} reason: {}", request, e.getMessage());
			request.completableFuture().ifPresent(c -> c.completeExceptionally(e));
		}
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddSuccessEventProcessor() {
		return mempoolAddSuccess -> {
			var req = inflightRequests.remove(mempoolAddSuccess.getTxn().getId());
			if (req == null) {
				return;
			}

			req.completableFuture().ifPresent(c -> c.complete(mempoolAddSuccess));
		};
	}

	public EventProcessor<MempoolAddFailure> mempoolAddFailureEventProcessor() {
		return failure -> {
			var req = inflightRequests.remove(failure.getTxn().getId());
			if (req == null) {
				return;
			}

			req.completableFuture().ifPresent(c -> c.completeExceptionally(failure.getException()));
		};
	}

	public EventProcessor<NodeApplicationRequest> requestEventProcessor() {
		return this::processRequest;
	}
}
