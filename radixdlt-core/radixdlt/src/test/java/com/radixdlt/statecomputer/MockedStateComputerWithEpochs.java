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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedTxn;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAdd;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MockedStateComputerWithEpochs implements StateComputer {
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final View epochHighView;
	private final MockedStateComputer stateComputer;

	@Inject
	public MockedStateComputerWithEpochs(
		@EpochCeilingView View epochHighView,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
		Hasher hasher
	) {
		this.validatorSetMapping = Objects.requireNonNull(validatorSetMapping);
		this.epochHighView = Objects.requireNonNull(epochHighView);
		this.stateComputer = new MockedStateComputer(ledgerUpdateDispatcher, hasher);
	}

	@Override
	public void addToMempool(MempoolAdd mempoolAdd, @Nullable BFTNode origin) {
	}

	@Override
	public List<Txn> getNextTxnsFromMempool(List<PreparedTxn> prepared) {
		return List.of();
	}

	@Override
	public StateComputerResult prepare(
		List<PreparedTxn> previous,
		VerifiedVertex vertex,
		long timestamp
	) {
		var view = vertex.getView();
		var epoch = vertex.getParentHeader().getLedgerHeader().getEpoch();
		var next = vertex.getTxns();
		if (view.compareTo(epochHighView) >= 0) {
			return new StateComputerResult(
				next.stream().map(MockPrepared::new).collect(Collectors.toList()),
				ImmutableMap.of(),
				validatorSetMapping.apply(epoch + 1)
			);
		} else {
			return stateComputer.prepare(previous, vertex, timestamp);
		}
	}

	@Override
	public void commit(VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		this.stateComputer.commit(verifiedTxnsAndProof, vertexStoreState);
	}
}
