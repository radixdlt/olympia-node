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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.MockPrepared;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolAdd;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MockedStateComputer implements StateComputer {
	private final EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher;
	private final Hasher hasher;

	public MockedStateComputer(
		EventDispatcher<LedgerUpdate> ledgerUpdateDispatcher,
		Hasher hasher
	) {
		this.ledgerUpdateDispatcher = ledgerUpdateDispatcher;
		this.hasher = hasher;
	}

	@Override
	public void addToMempool(MempoolAdd mempoolAdd, @Nullable BFTNode origin) {
	}

	@Override
	public List<Txn> getNextTxnsFromMempool(List<StateComputerLedger.PreparedTxn> prepared) {
		return List.of();
	}

	@Override
	public StateComputerLedger.StateComputerResult prepare(
		List<StateComputerLedger.PreparedTxn> previous,
		VerifiedVertex vertex,
		long timestamp
	) {
		return new StateComputerLedger.StateComputerResult(
			vertex.getTxns().stream().map(MockPrepared::new).collect(Collectors.toList()),
			Map.of()
		);
	}

	@Override
	public void commit(VerifiedTxnsAndProof txnsAndProof, VerifiedVertexStoreState vertexStoreState) {
		Optional<EpochChange> epochChangeOptional = txnsAndProof.getProof().getNextValidatorSet().map(validatorSet -> {
			LedgerProof header = txnsAndProof.getProof();
			UnverifiedVertex genesisVertex = UnverifiedVertex.createGenesis(header.getRaw());
			VerifiedVertex verifiedGenesisVertex = new VerifiedVertex(genesisVertex, hasher.hash(genesisVertex));
			LedgerHeader nextLedgerHeader = LedgerHeader.create(
				header.getEpoch() + 1,
				View.genesis(),
				header.getAccumulatorState(),
				header.timestamp()
			);
			QuorumCertificate genesisQC = QuorumCertificate.ofGenesis(verifiedGenesisVertex, nextLedgerHeader);
			final var initialState =
				VerifiedVertexStoreState.create(
					HighQC.from(genesisQC),
					verifiedGenesisVertex,
					Optional.empty(),
					hasher
				);
			var proposerElection = new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.getNode().getKey().euid()));
			var bftConfiguration = new BFTConfiguration(proposerElection, validatorSet, initialState);
			return new EpochChange(header, bftConfiguration);
		});

		var ledgerUpdate = new LedgerUpdate(txnsAndProof, epochChangeOptional);
		ledgerUpdateDispatcher.dispatch(ledgerUpdate);
	}
}
