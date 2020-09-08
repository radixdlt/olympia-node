/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import com.radixdlt.ledger.VerifiedCommandsAndProof;
import java.util.Optional;
import java.util.function.Function;

public class MockedStateComputerWithEpochsModule extends AbstractModule {
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final View epochHighView;

	public MockedStateComputerWithEpochsModule(
		View epochHighView,
		Function<Long, BFTValidatorSet> validatorSetMapping
	) {
		this.validatorSetMapping = validatorSetMapping;
		this.epochHighView = epochHighView;
	}

	@Provides
	private BFTValidatorSet genesisValidatorSet() {
		return validatorSetMapping.apply(1L);
	}

	@Provides
	private VerifiedLedgerHeaderAndProof genesisMetadata() {
		LedgerHeader ledgerHeader = LedgerHeader.create(0, View.genesis(), 0, Hash.ZERO_HASH, 0L, true);
		return VerifiedLedgerHeaderAndProof.ofGenesisAncestor(ledgerHeader);
	}

	@Provides
	private StateComputer stateComputer() {
		return new StateComputer() {
			@Override
			public boolean prepare(Vertex vertex) {
				return vertex.getView().compareTo(epochHighView) >= 0;
			}

			@Override
			public Optional<BFTValidatorSet> commit(VerifiedCommandsAndProof command) {
				if (command.getHeader().isEndOfEpoch()) {
					return Optional.of(validatorSetMapping.apply(command.getHeader().getEpoch() + 1));
				} else {
					return Optional.empty();
				}
			}
		};
	}
}
