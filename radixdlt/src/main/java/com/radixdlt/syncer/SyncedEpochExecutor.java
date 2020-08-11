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
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronizes execution
 */
public final class SyncedEpochExecutor implements SyncedExecutor<CommittedAtom> {

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	public interface SyncService {
		void sendLocalSyncRequest(LocalSyncRequest request);
	}

	public interface Executor {
		void execute(CommittedAtom committedAtom);
		boolean compute(Vertex vertex);
		BFTValidatorSet getValidatorSet(long epoch);
	}

	private final Mempool mempool;
	private final Executor executor;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final EpochChangeSender epochChangeSender;
	private final SystemCounters counters;
	private final SyncService syncService;

	private final Object lock = new Object();
	private final AtomicLong stateVersion;
	private VertexMetadata lastEpochChange = null;
	private final Map<Long, Set<Object>> committedStateSyncers = new HashMap<>();

	public SyncedEpochExecutor(
		long initialStateVersion,
		Mempool mempool,
		Executor executor,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		SyncService syncService,
		SystemCounters counters
	) {
		this.stateVersion = new AtomicLong(initialStateVersion);
		this.mempool = Objects.requireNonNull(mempool);
		this.executor = Objects.requireNonNull(executor);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.epochChangeSender = Objects.requireNonNull(epochChangeSender);
		this.counters = Objects.requireNonNull(counters);
		this.syncService = Objects.requireNonNull(syncService);
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
		synchronized (lock) {
			if (target.isEmpty()) {
				// TODO: relax this in future when we have non-validator nodes
				throw new IllegalArgumentException("target must not be empty");
			}

			final long targetStateVersion = vertexMetadata.getStateVersion();
			final long currentStateVersion = this.stateVersion.get();
			if (targetStateVersion <= currentStateVersion) {
				return true;
			}

			this.syncService.sendLocalSyncRequest(new LocalSyncRequest(targetStateVersion, currentStateVersion, target));
			this.committedStateSyncers.merge(targetStateVersion, Collections.singleton(opaque), Sets::union);

			return false;
		}
	}

	@Override
	public boolean compute(Vertex vertex) {
		return executor.compute(vertex);
	}

	/**
	 * Add an atom to the committed store
	 * @param atom the atom to commit
	 */
	@Override
	public void execute(CommittedAtom atom) {
		synchronized (lock) {
			this.counters.increment(CounterType.LEDGER_PROCESSED);

			final long stateVersion = atom.getVertexMetadata().getStateVersion();

			if (stateVersion != 0 && stateVersion != this.stateVersion.get() + 1) {
				return;
			}

			this.stateVersion.set(stateVersion);
			this.counters.set(CounterType.LEDGER_STATE_VERSION, stateVersion);

			this.executor.execute(atom);

			Set<Object> opaqueObjects = this.committedStateSyncers.remove(stateVersion);
			if (opaqueObjects != null) {
				for (Object opaque : opaqueObjects) {
					committedStateSyncSender.sendCommittedStateSync(stateVersion, opaque);
				}
			}

			if (atom.getClientAtom() != null) {
				this.mempool.removeCommittedAtom(atom.getAID());
			}

			// TODO: Move outside of syncedRadixEngine to a more generic syncing layer
			if (atom.getVertexMetadata().isEndOfEpoch()
				&& (lastEpochChange == null || lastEpochChange.getEpoch() != atom.getVertexMetadata().getEpoch())) {

				VertexMetadata ancestor = atom.getVertexMetadata();
				this.lastEpochChange = ancestor;
				EpochChange epochChange = new EpochChange(ancestor, executor.getValidatorSet(ancestor.getEpoch() + 1));
				this.epochChangeSender.epochChange(epochChange);
			}
		}
	}
}
