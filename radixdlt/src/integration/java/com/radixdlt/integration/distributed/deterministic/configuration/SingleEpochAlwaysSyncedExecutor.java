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
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.syncer.PreparedCommand;

/**
 * A state computer which never changes epochs
 */
public enum SingleEpochAlwaysSyncedExecutor implements SyncedExecutor {
	INSTANCE;

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, ImmutableList<BFTNode> target, Object opaque) {
		return true;
	}

	@Override
	public void commit(ClientAtom command, VertexMetadata vertexMetadata) {
		// No-op Mocked execution
	}

	@Override
	public PreparedCommand prepare(Vertex vertex) {
		return PreparedCommand.create(0, Hash.ZERO_HASH);
	}
}
