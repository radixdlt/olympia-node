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
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An update to the BFT state
 */
public final class BFTUpdate {
	private final Supplier<Stream<VerifiedVertex>> insertedVertices;
	private final int siblings;
	private final int vertexStoreSize;

	private BFTUpdate(Supplier<Stream<VerifiedVertex>> insertedVertices, int siblings, int vertexStoreSize) {
		this.insertedVertices = Objects.requireNonNull(insertedVertices);
		this.siblings = siblings;
		this.vertexStoreSize = vertexStoreSize;
	}

	public static BFTUpdate fromRebuild(VerifiedVertexStoreState vertexStoreState) {
		return new BFTUpdate(
			() -> Stream.concat(Stream.of(vertexStoreState.getRoot()), vertexStoreState.getVertices().stream()),
			0,
			vertexStoreState.getVertices().size()
		);
	}

	public static BFTUpdate insertedVertex(VerifiedVertex insertedVertex, int siblingsCount, VerifiedVertexStoreState vertexStoreState) {
		return new BFTUpdate(
			() -> Stream.of(insertedVertex),
			siblingsCount,
			vertexStoreState.getVertices().size()
		);
	}

	public int getSiblingsCount() {
		return siblings;
	}

	public int getVertexStoreSize() {
		return vertexStoreSize;
	}

	public Stream<VerifiedVertex> getInsertedVertices() {
		return insertedVertices.get();
	}

	@Override
	public String toString() {
		return String.format("%s", getClass().getSimpleName());
	}
}
