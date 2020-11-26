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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;

public class MockedStateComputerModule extends AbstractModule {
	@Provides
	private BFTConfiguration configuration(
		@LastEpochProof VerifiedLedgerHeaderAndProof proof,
		BFTValidatorSet validatorSet
	) {
		LedgerHeader nextLedgerHeader = LedgerHeader.create(
			proof.getEpoch() + 1,
			View.genesis(),
			proof.getAccumulatorState(),
			proof.timestamp()
		);
		UnverifiedVertex genesis = UnverifiedVertex.createGenesis(nextLedgerHeader);
		VerifiedVertex verifiedGenesis = new VerifiedVertex(genesis, HashUtils.zero256());
		QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesis, nextLedgerHeader);
		return new BFTConfiguration(validatorSet, verifiedGenesis, genesisQC);
	}

	@Provides
	@LastEpochProof
	private VerifiedLedgerHeaderAndProof lastEpochProof(BFTValidatorSet validatorSet) {
		return VerifiedLedgerHeaderAndProof.genesis(HashUtils.zero256(), validatorSet);
	}

	@Provides
	@LastProof
	private VerifiedLedgerHeaderAndProof lastProof(BFTConfiguration bftConfiguration) {
		return bftConfiguration.getRootHeader();
	}

	@Provides
	private StateComputer stateComputer(Hasher hasher) {
		return new StateComputer() {
			@Override
			public StateComputerResult prepare(ImmutableList<PreparedCommand> previous, Command next, long epoch, View view, long timestamp) {
				return new StateComputerResult(
					next == null
						? ImmutableList.of()
						: ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
					ImmutableMap.of()
				);
			}

			@Override
			public void commit(VerifiedCommandsAndProof command) {
				// No op
			}
		};
	}
}
