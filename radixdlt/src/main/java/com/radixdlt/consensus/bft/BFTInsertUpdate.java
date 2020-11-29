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

package com.radixdlt.consensus.bft;

import java.util.Objects;

/**
 * An update to the BFT state
 */
public final class BFTInsertUpdate {
	private final VerifiedVertexStoreState vertexStoreState;
	private final VerifiedVertex insertedVertex;
	private final int siblings;

	private BFTInsertUpdate(VerifiedVertexStoreState vertexStoreState, VerifiedVertex insertedVertex, int siblings) {
		this.vertexStoreState = vertexStoreState;
		this.insertedVertex = Objects.requireNonNull(insertedVertex);
		this.siblings = siblings;
	}

	public static BFTInsertUpdate insertedVertex(VerifiedVertex insertedVertex, int siblingsCount, VerifiedVertexStoreState vertexStoreState) {
		return new BFTInsertUpdate(
			vertexStoreState,
			insertedVertex,
			siblingsCount
		);
	}

	public VerifiedVertexStoreState getVertexStoreState() {
		return vertexStoreState;
	}

	public int getSiblingsCount() {
		return siblings;
	}

	public int getVertexStoreSize() {
		return vertexStoreState.getVertices().size();
	}

	public VerifiedVertex getInsertedVertex() {
		return insertedVertex;
	}

	@Override
	public String toString() {
		return String.format("%s", getClass().getSimpleName());
	}
}
