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

package com.radixdlt.consensus.deterministic.configuration;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.syncer.SyncedEpochExecutor.CommittedStateSyncSender;
import com.radixdlt.middleware2.CommittedAtom;
import java.util.Objects;
import java.util.Random;

public class SingleEpochRandomlySyncedExecutor implements SyncedExecutor<CommittedAtom> {

	private final Random random;
	private final CommittedStateSyncSender committedStateSyncSender;

	public SingleEpochRandomlySyncedExecutor(Random random, CommittedStateSyncSender committedStateSyncSender) {
		this.random = Objects.requireNonNull(random);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
		if (random.nextBoolean()) {
			return true;
		}
		committedStateSyncSender.sendCommittedStateSync(vertexMetadata.getStateVersion(), opaque);
		return false;
	}

	@Override
	public boolean compute(Vertex vertex) {
		return false;
	}

	@Override
	public void execute(CommittedAtom instruction) {
		// No-op Mocked execution
	}
}
