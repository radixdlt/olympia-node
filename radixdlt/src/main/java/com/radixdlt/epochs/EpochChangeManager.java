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

package com.radixdlt.epochs;

import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import java.util.Objects;
import java.util.Optional;

/**
 * Translates committed commands to epoch change messages
 */
public final class EpochChangeManager implements LedgerUpdateSender {
	public interface EpochsLedgerUpdateSender {
		void sendLedgerUpdate(EpochsLedgerUpdate epochsLedgerUpdate);
	}

	private final EpochsLedgerUpdateSender epochsLedgerUpdateSender;
	private final Hasher hasher;

	public EpochChangeManager(EpochsLedgerUpdateSender epochsLedgerUpdateSender, Hasher hasher) {
		this.epochsLedgerUpdateSender = Objects.requireNonNull(epochsLedgerUpdateSender);
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public void sendLedgerUpdate(LedgerUpdate ledgerUpdate) {
		Optional<EpochChange> epochChangeOptional = ledgerUpdate.getNextValidatorSet().map(validatorSet -> {
			VerifiedLedgerHeaderAndProof header = ledgerUpdate.getTail();
			UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(header.getRaw());
			VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
			LedgerHeader nextLedgerHeader = LedgerHeader.create(
				header.getEpoch() + 1,
				View.genesis(),
				header.getAccumulatorState(),
				header.timestamp()
			);
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
			VerifiedVertexStoreState initialState = VerifiedVertexStoreState.create(genesisQC, verifiedGenesisVertex);
			BFTConfiguration bftConfiguration = new BFTConfiguration(validatorSet, initialState);
			return new EpochChange(header, bftConfiguration);
		});

		EpochsLedgerUpdate epochsLedgerUpdate = new EpochsLedgerUpdate(ledgerUpdate, epochChangeOptional.orElse(null));
		this.epochsLedgerUpdateSender.sendLedgerUpdate(epochsLedgerUpdate);
	}
}
