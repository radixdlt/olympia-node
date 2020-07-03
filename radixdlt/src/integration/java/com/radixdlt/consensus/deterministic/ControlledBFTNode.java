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

package com.radixdlt.consensus.deterministic;

import static org.mockito.Mockito.mock;

import com.radixdlt.consensus.CommittedStateSync;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EmptySyncEpochsRPCSender;
import com.radixdlt.consensus.EmptySyncVerticesRPCSender;
import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.GetVerticesResponse;
import com.radixdlt.consensus.LocalTimeout;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.VertexStore.GetVerticesRequest;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.deterministic.ControlledBFTNetwork.ControlledSender;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Controlled BFT Node where its state machine is managed by a synchronous
 * processNext() call.
 */
class ControlledBFTNode {
	private final EpochManager epochManager;
	private final SystemCounters systemCounters;
	private final ValidatorSet initialValidatorSet;
	private final ControlledSender controlledSender;

	ControlledBFTNode(
		ECKeyPair key,
		ControlledSender sender,
		ProposerElectionFactory proposerElectionFactory,
		ValidatorSet initialValidatorSet,
		boolean enableGetVerticesRPC,
		BooleanSupplier syncedSupplier
	) {
		this.systemCounters = new SystemCountersImpl();
		this.controlledSender = Objects.requireNonNull(sender);
		this.initialValidatorSet = Objects.requireNonNull(initialValidatorSet);

		SyncedStateComputer<CommittedAtom> stateComputer = new SyncedStateComputer<CommittedAtom>() {
			@Override
			public boolean syncTo(VertexMetadata vertexMetadata, List<ECPublicKey> target, Object opaque) {
				if (syncedSupplier.getAsBoolean()) {
					return true;
				}

				sender.committedStateSync(new CommittedStateSync(vertexMetadata.getStateVersion(), opaque));
				return false;
			}

			@Override
			public boolean compute(Vertex vertex) {
				return false;
			}

			@Override
			public void execute(CommittedAtom instruction) {
			}
		};

		SyncVerticesRPCSender syncVerticesRPCSender = enableGetVerticesRPC ? sender : EmptySyncVerticesRPCSender.INSTANCE;
		Mempool mempool = new EmptyMempool();
		Hasher hasher = new DefaultHasher();
		VertexStoreFactory vertexStoreFactory = (vertex, qc, syncedStateComputer) ->
			new VertexStore(vertex, qc, syncedStateComputer, syncVerticesRPCSender, sender, systemCounters);

		this.epochManager = new EpochManager(
			stateComputer,
			mempool,
			sender,
			EmptySyncEpochsRPCSender.INSTANCE,
			mock(ScheduledTimeoutSender.class),
			timeoutSender -> new FixedTimeoutPacemaker(1, timeoutSender),
			vertexStoreFactory,
			proposerElectionFactory,
			hasher,
			key,
			systemCounters
		);
	}

	SystemCounters getSystemCounters() {
		return systemCounters;
	}

	void start() {
		EpochChange epochChange = new EpochChange(VertexMetadata.ofGenesisAncestor(), this.initialValidatorSet);
		controlledSender.epochChange(epochChange);
	}

	void processNext(Object msg) {
		if (msg instanceof EpochChange) {
			this.epochManager.processEpochChange((EpochChange) msg);
		} else if (msg instanceof GetVerticesRequest) {
			this.epochManager.processGetVerticesRequest((GetVerticesRequest) msg);
		} else if (msg instanceof GetVerticesResponse) {
			this.epochManager.processGetVerticesResponse((GetVerticesResponse) msg);
		} else if (msg instanceof CommittedStateSync) {
			this.epochManager.processCommittedStateSync((CommittedStateSync) msg);
		} else if (msg instanceof LocalTimeout) {
			this.epochManager.processLocalTimeout((LocalTimeout) msg);
		} else if (msg instanceof ConsensusEvent) {
			this.epochManager.processConsensusEvent((ConsensusEvent) msg);
		} else if (msg instanceof Hash) {
			this.epochManager.processLocalSync((Hash) msg);
		} else {
			throw new IllegalStateException("Unknown msg: " + msg);
		}
	}
}
