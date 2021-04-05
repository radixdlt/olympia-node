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

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedTxnsAndProof;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MockedStateComputer implements StateComputer {
	public MockedStateComputer() {
	}

	@Override
	public void addToMempool(Txn txn, BFTNode origin) {
		// No-op
	}

	@Override
	public List<Txn> getNextTxnsFromMempool(List<StateComputerLedger.PreparedTxn> prepared) {
		return List.of();
	}

	@Override
	public StateComputerLedger.StateComputerResult prepare(
		List<StateComputerLedger.PreparedTxn> previous,
		List<Txn> next,
		long epoch,
		View view,
		long timestamp
	) {
		return new StateComputerLedger.StateComputerResult(
			next.stream().map(MockPrepared::new).collect(Collectors.toList()),
			Map.of()
		);
	}

	@Override
	public void commit(VerifiedTxnsAndProof verifiedTxnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		// No-op
	}
}
