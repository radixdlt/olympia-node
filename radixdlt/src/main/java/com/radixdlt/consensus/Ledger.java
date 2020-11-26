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

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.PreparedVertex;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
import java.util.LinkedList;
import java.util.Optional;

/**
 * A distributed computer which manages the computed state in a BFT.
 */
public interface Ledger {
	/**
	 * Given a proposed vertex, executes prepare stage on
	 * the state computer, the result of which gets persisted on ledger.
	 *
	 * @param previous parents of given vertex
	 * @param vertex vertex to prepare
	 * @return the results of executing the prepare stage
	 */
	Optional<PreparedVertex> prepare(LinkedList<PreparedVertex> previous, VerifiedVertex vertex);

	/**
	 * Commit prepared vertices from bft consensus
	 * @param prunedVertices vertices which no longer can be committed
	 * @param vertices vertices to commit
	 */
	void commit(
		ImmutableSet<HashCode> prunedVertices,
		ImmutableList<PreparedVertex> vertices,
		VerifiedVertexStoreState vertexStoreState
	);

	/**
	 * Commit commands
	 * @param verifiedCommandsAndProof the command to commit
	 */
	void commit(VerifiedCommandsAndProof verifiedCommandsAndProof);
}
