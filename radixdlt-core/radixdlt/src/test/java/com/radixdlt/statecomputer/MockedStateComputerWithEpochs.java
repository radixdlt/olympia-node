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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

import java.util.Objects;
import java.util.function.Function;

public final class MockedStateComputerWithEpochs implements StateComputer {
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final View epochHighView;
	private final Hasher hasher;
	private final MockedStateComputer stateComputer;

	public MockedStateComputerWithEpochs(
		Hasher hasher,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		View epochHighView
	) {
		this.hasher = Objects.requireNonNull(hasher);
		this.validatorSetMapping = Objects.requireNonNull(validatorSetMapping);
		this.epochHighView = Objects.requireNonNull(epochHighView);
		this.stateComputer = new MockedStateComputer(hasher);
	}

	@Override
	public void addToMempool(Command command) {
		// No-op
	}

	@Override
	public Command getNextCommandFromMempool(ImmutableList<StateComputerLedger.PreparedCommand> prepared) {
		return null;
	}

	@Override
	public StateComputerResult prepare(
		ImmutableList<PreparedCommand> previous,
		Command next,
		long epoch,
		View view,
		long timestamp
	) {
		if (view.compareTo(epochHighView) >= 0) {
			return new StateComputerResult(
				next == null ? ImmutableList.of() : ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
				ImmutableMap.of(),
				validatorSetMapping.apply(epoch + 1)
			);
		} else {
			return stateComputer.prepare(previous, next, epoch, view, timestamp);
		}
	}

	@Override
	public void commit(VerifiedCommandsAndProof verifiedCommandsAndProof, VerifiedVertexStoreState vertexStoreState) {
		// No-op
	}
}
