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

package com.radixdlt.statecomputer;

import com.radixdlt.application.system.NextValidatorSetEvent;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.engine.PostProcessor;
import com.radixdlt.engine.PostProcessorException;
import com.radixdlt.store.EngineStore;

import java.util.List;
import java.util.stream.Collectors;

public class EpochProofVerifierV2 implements PostProcessor<LedgerAndBFTProof> {
	@Override
	public LedgerAndBFTProof process(
		LedgerAndBFTProof metadata,
		EngineStore<LedgerAndBFTProof> engineStore,
		List<REProcessedTxn> txns
	) throws PostProcessorException {
		NextValidatorSetEvent nextValidatorSetEvent = null;
		for (int i = 0; i < txns.size(); i++) {
			var processed = txns.get(i);
			var nextEpochEvents = processed.getEvents().stream()
				.filter(NextValidatorSetEvent.class::isInstance)
				.map(NextValidatorSetEvent.class::cast)
				.collect(Collectors.toList());

			if (!nextEpochEvents.isEmpty()) {
				// TODO: Move this check into Meter
				if (i != txns.size() - 1) {
					throw new PostProcessorException("Additional txns added to end of epoch.");
				}

				// TODO: Move this check into Meter
				if (nextEpochEvents.size() != 1) {
					throw new PostProcessorException("Multiple epoch events occurred in batch.");
				}

				// TODO: Move this check into Meter
				var stateUpdates = processed.getGroupedStateUpdates();
				if (stateUpdates.get(stateUpdates.size() - 1).stream()
					.noneMatch(u -> u.getParsed() instanceof EpochData)) {
					throw new PostProcessorException("Epoch update is not the last execution.");
				}

				nextValidatorSetEvent = nextEpochEvents.get(0);
			}
		}

		var nextValidatorSetMaybe = metadata.getProof().getNextValidatorSet();
		if (nextValidatorSetEvent == null != nextValidatorSetMaybe.isEmpty()) {
			throw new PostProcessorException("Epoch event does not match proof " + nextValidatorSetEvent + " " + nextValidatorSetMaybe);
		}
		if (nextValidatorSetEvent != null) {
			// TODO: Comparison of ordering as well
			var nextValidatorSet = nextValidatorSetEvent.nextValidators().stream()
				.map(v -> BFTValidator.from(BFTNode.create(v.getValidatorKey()), v.getAmount()));
			var bftValidatorSet = BFTValidatorSet.from(nextValidatorSet);
			if (!nextValidatorSetMaybe.orElseThrow().equals(bftValidatorSet)) {
				throw new PostProcessorException("Validator set computed does not match proof.");
			}
		}

		return metadata;
	}
}
