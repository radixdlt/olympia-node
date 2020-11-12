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
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
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
	private BFTConfiguration initialConfiguration(
		BFTValidatorSet validatorSet,
		@LastEpochProof VerifiedLedgerHeaderAndProof proof,
		Hasher hasher
	) {
		UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(proof.getRaw());
		VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getAccumulatorState(),
			proof.timestamp()
		);
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
		return new BFTConfiguration(
			validatorSet,
			verifiedGenesisVertex,
			genesisQC
		);
	}

	@Provides
	@LastEpochProof
	private VerifiedLedgerHeaderAndProof lastEpochProof(BFTValidatorSet validatorSet) {
		return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
	}


	@Provides
	@LastProof
	private VerifiedLedgerHeaderAndProof lastProof(BFTConfiguration bftConfiguration) {
		return bftConfiguration.getGenesisHeader();
	}

	@Provides
	@Singleton
	private StateComputer stateComputer(Hasher hasher) {
		return new StateComputer() {

			@Override
			public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timstamp) {
				if (view.compareTo(epochHighView) >= 0) {
					return new StateComputerResult(
						next == null ? ImmutableList.of() : ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
						ImmutableMap.of(),
						validatorSetMapping.apply(epoch + 1)
					);
				} else {
					return new StateComputerResult(
						next == null ? ImmutableList.of() : ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
						ImmutableMap.of()
					);
				}
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
			}
		};
	}
}
