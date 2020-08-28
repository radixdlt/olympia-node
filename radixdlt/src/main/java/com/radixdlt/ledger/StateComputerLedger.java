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

import com.google.common.collect.Sets;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.PreparedCommand;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hash;
import com.radixdlt.mempool.Mempool;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Synchronizes execution
 */
public final class StateComputerLedger implements Ledger, NextCommandGenerator {
	public interface StateComputer {
		boolean prepare(Vertex vertex);
		Optional<BFTValidatorSet> commit(Command command, VertexMetadata vertexMetadata);
	}

	public interface CommittedSender {
		// TODO: batch these
		void sendCommitted(CommittedCommand committedCommand, BFTValidatorSet validatorSet);
	}

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	private final Mempool mempool;
	private final StateComputer stateComputer;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final CommittedSender committedSender;
	private final SystemCounters counters;

	private final Object lock = new Object();
	private long currentStateVersion;
	private final Map<Long, Set<Object>> committedStateSyncers = new HashMap<>();

	public StateComputerLedger(
		long initialStateVersion,
		Mempool mempool,
		StateComputer stateComputer,
		CommittedStateSyncSender committedStateSyncSender,
		CommittedSender committedSender,
		SystemCounters counters
	) {
		this.currentStateVersion = initialStateVersion;
		this.mempool = Objects.requireNonNull(mempool);
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.committedSender = Objects.requireNonNull(committedSender);
		this.counters = Objects.requireNonNull(counters);
	}

	@Override
	public Command generateNextCommand(View view, Set<Hash> prepared) {
		final List<Command> commands = mempool.getCommands(1, prepared);
		return !commands.isEmpty() ? commands.get(0) : null;
	}

	@Override
	public PreparedCommand prepare(Vertex vertex) {
		final PreparedCommand parent = vertex.getQC().getProposed().getPreparedCommand();
		final long parentStateVersion = parent.getStateVersion();

		boolean isEndOfEpoch = stateComputer.prepare(vertex);

		final int versionIncrement;
		if (parent.isEndOfEpoch()) {
			versionIncrement = 0; // Don't execute atom if in process of epoch change
		} else if (isEndOfEpoch) {
			versionIncrement = 1;
		} else {
			versionIncrement = vertex.getCommand() != null ? 1 : 0;
		}

		final long stateVersion = parentStateVersion + versionIncrement;
		final long timestamp = vertex.getQC().getTimestampedSignatures().weightedTimestamp();

		return PreparedCommand.create(stateVersion, timestamp, isEndOfEpoch);
	}

	@Override
	public OnSynced ifCommitSynced(VertexMetadata vertexMetadata) {
		final long targetStateVersion = vertexMetadata.getPreparedCommand().getStateVersion();
		synchronized (lock) {
			if (targetStateVersion <= this.currentStateVersion) {
				return onSync -> {
					onSync.run();
					return (onNotSynced, opaque) -> { };
				};
			} else {
				return onSync -> (onNotSynced, opaque) -> {
					this.committedStateSyncers.merge(targetStateVersion, Collections.singleton(opaque), Sets::union);
					onNotSynced.run();
				};
			}
		}
	}

	@Override
	public void commit(Command command, VertexMetadata vertexMetadata) {
		this.counters.increment(CounterType.LEDGER_PROCESSED);

		synchronized (lock) {
			final long stateVersion = vertexMetadata.getPreparedCommand().getStateVersion();
			// TODO: get this invariant to as low level as possible
			if (stateVersion != this.currentStateVersion + 1) {
				return;
			}

			this.currentStateVersion = stateVersion;
			this.counters.set(CounterType.LEDGER_STATE_VERSION, this.currentStateVersion);

			// persist
			Optional<BFTValidatorSet> validatorSet = this.stateComputer.commit(command, vertexMetadata);
			// TODO: move all of the following to post-persist event handling
			if (command != null) {
				this.mempool.removeCommitted(command.getHash());
			}
			CommittedCommand committedCommand = new CommittedCommand(command, vertexMetadata);
			committedSender.sendCommitted(committedCommand, validatorSet.orElse(null));

			Set<Object> opaqueObjects = this.committedStateSyncers.remove(this.currentStateVersion);
			if (opaqueObjects != null) {
				for (Object opaque : opaqueObjects) {
					committedStateSyncSender.sendCommittedStateSync(this.currentStateVersion, opaque);
				}
			}
		}
	}
}
