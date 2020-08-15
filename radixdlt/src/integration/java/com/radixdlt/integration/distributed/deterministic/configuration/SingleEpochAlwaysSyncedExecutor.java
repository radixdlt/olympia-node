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

package com.radixdlt.integration.distributed.deterministic.configuration;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.syncer.ExecutionResult;

/**
 * A state computer which never changes epochs
 */
public enum SingleEpochAlwaysSyncedExecutor implements SyncedExecutor<CommittedAtom> {
	INSTANCE;

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
		return true;
	}

	@Override
	public ExecutionResult execute(Vertex vertex) {
		return ExecutionResult.create(0, Hash.ZERO_HASH, false);
	}

	@Override
	public void commit(CommittedAtom instruction) {
		// No-op Mocked execution
	}
}
