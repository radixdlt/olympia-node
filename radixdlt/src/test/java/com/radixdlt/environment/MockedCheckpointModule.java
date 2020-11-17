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

package com.radixdlt.environment;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.UInt256;
import java.util.List;
import java.util.Objects;

public final class MockedCheckpointModule extends AbstractModule {
	@Provides
	VerifiedCommandsAndProof genesis(List<BFTNode> initialValidators, Serialization serialization, Hasher hasher) {
		BFTValidatorSet validatorSet = BFTValidatorSet.from(initialValidators.stream().map(node -> BFTValidator.from(node, UInt256.ONE)));

		final ClientAtom genesisAtom = ClientAtom.create(
			ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(new SystemParticle(0, 0, 0), Spin.UP),
				CMMicroInstruction.checkSpinAndPush(new SystemParticle(1, 0, 0), Spin.NEUTRAL)
			),
			hasher
		);
		byte[] payload = serialization.toDson(genesisAtom, Output.ALL);
		Command command = new Command(payload);
		// Checkpoint
		VerifiedLedgerHeaderAndProof genesisLedgerHeader = VerifiedLedgerHeaderAndProof.genesis(
			HashUtils.zero256(),
			validatorSet
		);
		return new VerifiedCommandsAndProof(
			ImmutableList.of(command),
			genesisLedgerHeader
		);
	}
}
