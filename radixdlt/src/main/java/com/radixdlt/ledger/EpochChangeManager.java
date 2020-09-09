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

package com.radixdlt.ledger;

import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import java.util.Objects;

/**
 * Translates committed commands to epoch change messages
 */
public final class EpochChangeManager implements CommittedSender {
	private final EpochChangeSender epochChangeSender;
	private final Hasher hasher;

	public EpochChangeManager(EpochChangeSender epochChangeSender, Hasher hasher) {
		this.epochChangeSender = Objects.requireNonNull(epochChangeSender);
		this.hasher = Objects.requireNonNull(hasher);
	}

	@Override
	public void sendCommitted(VerifiedCommandsAndProof commandsAndProof, BFTValidatorSet validatorSet) {
		if (validatorSet != null) {
			VerifiedLedgerHeaderAndProof proof = commandsAndProof.getHeader();
			UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(commandsAndProof.getHeader().getRaw());
			VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
			LedgerHeader nextLedgerHeader = LedgerHeader.create(
				proof.getEpoch() + 1,
				View.genesis(),
				proof.getStateVersion(),
				proof.getCommandId(),
				proof.timestamp(),
				false
			);
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
			BFTConfiguration bftConfiguration = new BFTConfiguration(validatorSet, verifiedGenesisVertex, genesisQC);
			EpochChange epochChange = new EpochChange(proof, bftConfiguration);
			this.epochChangeSender.epochChange(epochChange);
		}
	}
}
