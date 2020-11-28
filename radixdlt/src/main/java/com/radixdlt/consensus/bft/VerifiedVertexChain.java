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
import java.util.List;

public class VerifiedVertexChain {
	private final ImmutableList<VerifiedVertex> vertices;

	private VerifiedVertexChain(ImmutableList<VerifiedVertex> vertices) {
		this.vertices = vertices;
	}

	public static VerifiedVertexChain create(List<VerifiedVertex> vertices) {
		if (vertices.size() >= 2) {
			for (int index = 1; index < vertices.size(); index++) {
				if (vertices.get(index - 1).getId().equals(vertices.get(index).getParentId())) {
					throw new IllegalStateException("Invalid chain");
				}
			}
		}

		return new VerifiedVertexChain(ImmutableList.copyOf(vertices));
	}

	public ImmutableList<VerifiedVertex> getVertices() {
		return vertices;
	}
}
