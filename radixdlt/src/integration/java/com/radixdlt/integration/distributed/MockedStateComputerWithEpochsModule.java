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

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
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
	private BFTConfiguration initialConfiguration(BFTValidatorSet validatorSet, VerifiedLedgerHeaderAndProof proof, Hasher hasher) {
		UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(proof.getRaw());
		VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getAccumulatorState(),
			proof.timestamp(),
			null
		);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
		return new BFTConfiguration(
			validatorSet,
			verifiedGenesisVertex,
			genesisQC
		);
	}

	@Provides
	private VerifiedLedgerHeaderAndProof genesisProof(BFTValidatorSet validatorSet) {
		return VerifiedLedgerHeaderAndProof.genesis(Hash.ZERO_HASH, validatorSet);
	}

	@Provides
	@Singleton
	private StateComputer stateComputer() {
		return new StateComputer() {
			private long epoch = 1;
			@Override
			public StateComputerResult prepare(ImmutableList<Command> commands, View view) {
				if (view.compareTo(epochHighView) >= 0) {
					return new StateComputerResult(ImmutableSet.of(), validatorSetMapping.apply(epoch + 1));
				} else {
					return new StateComputerResult();
				}
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
				if (command.getHeader().isEndOfEpoch()) {
					epoch = command.getHeader().getEpoch() + 1;
				}
			}
		};
	}
}
