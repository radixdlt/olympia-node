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

package com.radixdlt.syncer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Synchronizes execution
 */
public final class SyncExecutor implements SyncedExecutor<CommittedAtom>, NextCommandGenerator {


	public interface SyncService {
		void sendLocalSyncRequest(LocalSyncRequest request);
	}

	public interface StateComputerExecutedCommand {
		interface StateComputerExecutedCommandMaybeError {
			void elseIfError(BiConsumer<CommittedAtom, Exception> errorConsumer);
		}

		CommittedAtom getCommand();
		StateComputerExecutedCommandMaybeError ifSuccess(BiConsumer<CommittedAtom, Object> successConsumer);
	}

	public interface StateComputer {
		// TODO: Remove commit and move functionality into execute
		StateComputerExecutedCommand commit(CommittedAtom committedAtom);
		Optional<BFTValidatorSet> prepare(Vertex vertex);
	}

	public interface CommittedSender {
		// TODO: batch these
		void sendCommitted(StateComputerExecutedCommand stateComputerExecutedCommand);
	}

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	private final Mempool mempool;
	private final StateComputer stateComputer;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final CommittedSender committedSender;
	private final SystemCounters counters;
	private final SyncService syncService;

	private final Object lock = new Object();
	private long currentStateVersion;
	private final Map<Long, Set<Object>> committedStateSyncers = new HashMap<>();

	public SyncExecutor(
		long initialStateVersion,
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		CommittedSender committedSender,
		SyncService syncService,
		SystemCounters counters
	) {
		this.currentStateVersion = initialStateVersion;
		this.mempool = Objects.requireNonNull(mempool);
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.committedSender = Objects.requireNonNull(committedSender);
		this.counters = Objects.requireNonNull(counters);
		this.syncService = Objects.requireNonNull(syncService);
	}

	@Override
	public ClientAtom generateNextCommand(View view, Set<AID> prepared) {
		final List<ClientAtom> atoms = mempool.getAtoms(1, prepared);
		return !atoms.isEmpty() ? atoms.get(0) : null;
	}

	@Override
	public PreparedCommand prepare(Vertex vertex) {
		final VertexMetadata parent = vertex.getQC().getProposed();
		final long parentStateVersion = parent.getStateVersion();

		Optional<BFTValidatorSet> validatorSet = stateComputer.prepare(vertex);

		final int versionIncrement;
		if (parent.isEndOfEpoch()) {
			versionIncrement = 0; // Don't execute atom if in process of epoch change
		} else if (validatorSet.isPresent()) {
			versionIncrement = 1;
		} else {
			versionIncrement = vertex.getAtom() != null ? 1 : 0;
		}

		final long stateVersion = parentStateVersion + versionIncrement;
		final Hash timestampedSignaturesHash = vertex.getQC().getTimestampedSignatures().getId();

		return validatorSet
			.map(vset -> PreparedCommand.create(stateVersion, timestampedSignaturesHash, vset))
			.orElseGet(() -> PreparedCommand.create(stateVersion, timestampedSignaturesHash));
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
		synchronized (lock) {
			if (target.isEmpty()) {
				// TODO: relax this in future when we have non-validator nodes
				throw new IllegalArgumentException("target must not be empty");
			}

			final long targetStateVersion = vertexMetadata.getStateVersion();
			if (targetStateVersion <= this.currentStateVersion) {
				return true;
			}

			this.syncService.sendLocalSyncRequest(new LocalSyncRequest(vertexMetadata, currentStateVersion, target));
			this.committedStateSyncers.merge(targetStateVersion, Collections.singleton(opaque), Sets::union);

			return false;
		}
	}

	/**
	 * Add an atom to the committed store
	 * @param atom the atom to commit
	 */
	@Override
	public void commit(CommittedAtom atom) {
		synchronized (lock) {
			this.counters.increment(CounterType.LEDGER_PROCESSED);

			final long stateVersion = atom.getVertexMetadata().getStateVersion();
			if (stateVersion != this.currentStateVersion + 1) {
				return;
			}

			this.currentStateVersion = stateVersion;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentStateVersion);

			StateComputerExecutedCommand result = this.stateComputer.commit(atom);
			if (atom.getClientAtom() != null) {
				this.mempool.removeCommittedAtom(atom.getAID());
			}

			committedSender.sendCommitted(result);

			Set<Object> opaqueObjects = this.committedStateSyncers.remove(this.currentStateVersion);
			if (opaqueObjects != null) {
				for (Object opaque : opaqueObjects) {
					committedStateSyncSender.sendCommittedStateSync(this.currentStateVersion, opaque);
				}
			}
		}
	}
}
