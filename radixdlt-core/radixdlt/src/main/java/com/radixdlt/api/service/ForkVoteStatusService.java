/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.service;

import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.radixdlt.application.validators.state.ValidatorSystemMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.engine.RadixEngine.RadixEngineResult;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.InitialForkConfig;
import com.radixdlt.store.EngineStore;
import org.json.JSONObject;

import java.util.Objects;

public class ForkVoteStatusService {

	public enum ForkVoteStatus {
		VOTE_REQUIRED, NO_ACTION_NEEDED
	}

	private final BFTNode self;
	private final EngineStore<LedgerAndBFTProof> engineStore;
	private final Forks forks;
	private ForkConfig currentForkConfig;

	@Inject
	public ForkVoteStatusService(
		@Self BFTNode self,
		EngineStore<LedgerAndBFTProof> engineStore,
		Forks forks,
		@InitialForkConfig ForkConfig initialForkConfig
	) {
		this.self = Objects.requireNonNull(self);
		this.engineStore = Objects.requireNonNull(engineStore);
		this.forks = Objects.requireNonNull(forks);
		this.currentForkConfig = Objects.requireNonNull(initialForkConfig);
	}

	public JSONObject currentFork() {
		return SystemConfigService.forkConfigJson(currentForkConfig);
	}

	public ForkVoteStatus forkVoteStatus() {
		if (forks.getCandidateFork().isEmpty()) {
			return ForkVoteStatus.NO_ACTION_NEEDED;
		}

		final var expectedCandidateForkVoteHash =
			ForkConfig.voteHash(self.getKey(), forks.getCandidateFork().get());

		final var substateDeserialization = currentForkConfig.engineRules().getParser().getSubstateDeserialization();

		// TODO: this could be optimized
		try (var validatorMetadataCursor = engineStore.openIndexedCursor(
				SubstateIndex.create(SubstateTypeId.VALIDATOR_SYSTEM_META_DATA.id(), ValidatorSystemMetadata.class))) {

			final var maybeSelfForkVoteHash = Streams.stream(validatorMetadataCursor)
				.map(s -> {
					try {
						return (ValidatorSystemMetadata) substateDeserialization.deserialize(s.getData());
					} catch (DeserializeException e) {
						throw new IllegalStateException("Failed to deserialize ValidatorMetaData substate");
					}
				})
				.filter(vm -> vm.getValidatorKey().equals(self.getKey()))
				.findAny()
				.map(ValidatorSystemMetadata::getAsHash);

			return maybeSelfForkVoteHash.filter(expectedCandidateForkVoteHash::equals).isPresent()
				? ForkVoteStatus.NO_ACTION_NEEDED
				: ForkVoteStatus.VOTE_REQUIRED;
		}
	}

	@SuppressWarnings("unchecked")
	public EventProcessor<LedgerUpdate> ledgerUpdateEventProcessor() {
		return ledgerUpdate -> {
			final var result = (RadixEngineResult<LedgerAndBFTProof>) ledgerUpdate.getStateComputerOutput().get(RadixEngineResult.class);
			result.getMetadata().getNextForkHash()
				.flatMap(forks::getByHash)
				.ifPresent(nextFork -> currentForkConfig = nextFork);
		};
	}
}
