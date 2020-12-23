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
 * An update emitted when the BFT has been rebuilt
 */
public final class BFTRebuildUpdate {
	private final VerifiedVertexStoreState vertexStoreState;

	private BFTRebuildUpdate(VerifiedVertexStoreState vertexStoreState) {
		this.vertexStoreState = vertexStoreState;
	}

	public static BFTRebuildUpdate create(VerifiedVertexStoreState vertexStoreState) {
		return new BFTRebuildUpdate(vertexStoreState);
	}

	public VerifiedVertexStoreState getVertexStoreState() {
		return vertexStoreState;
	}


	@Override
	public String toString() {
		return String.format("%s{root=%s}", this.getClass().getSimpleName(), vertexStoreState.getRoot());
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertexStoreState);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof BFTRebuildUpdate)) {
			return false;
		}

		BFTRebuildUpdate other = (BFTRebuildUpdate) o;
		return Objects.equals(other.vertexStoreState, this.vertexStoreState);
	}
}
