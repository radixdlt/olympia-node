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

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import java.util.List;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * A chain of vertices verified to be consistent
 */
@Immutable
public final class VerifiedVertexChain {
	private final ImmutableList<VerifiedVertex> vertices;

	private VerifiedVertexChain(ImmutableList<VerifiedVertex> vertices) {
		this.vertices = vertices;
	}

	public static VerifiedVertexChain create(List<VerifiedVertex> vertices) {
		if (vertices.size() >= 2) {
			for (int index = 1; index < vertices.size(); index++) {
				HashCode parentId = vertices.get(index - 1).getId();
				HashCode parentIdCheck = vertices.get(index).getParentId();
				if (!parentId.equals(parentIdCheck)) {
					throw new IllegalArgumentException(String.format("Invalid chain: %s", vertices));
				}
			}
		}

		return new VerifiedVertexChain(ImmutableList.copyOf(vertices));
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}

	@Override
	public String toString() {
		return String.format("%s{vertices=%s}", this.getClass().getSimpleName(), this.vertices);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(vertices);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof VerifiedVertexChain)) {
			return false;
		}

		VerifiedVertexChain other = (VerifiedVertexChain) o;
		return Objects.equals(this.vertices, other.vertices);
	}
}
