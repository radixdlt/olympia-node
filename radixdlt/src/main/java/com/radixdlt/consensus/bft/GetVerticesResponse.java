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

import com.radixdlt.consensus.Vertex;
import com.radixdlt.crypto.Hash;
import java.util.List;
import java.util.Objects;

/**
 * An RPC response
 */
public final class GetVerticesResponse {
	private final Object opaque;
	private final Hash vertexId;
	private final List<Vertex> vertices;

	public GetVerticesResponse(Hash vertexId, List<Vertex> vertices, Object opaque) {
		this.vertexId = Objects.requireNonNull(vertexId);
		this.vertices = Objects.requireNonNull(vertices);
		this.opaque = opaque;
	}

	public Object getOpaque() {
		return opaque;
	}

	public Hash getVertexId() {
		return vertexId;
	}

	public List<Vertex> getVertices() {
		return vertices;
	}

	@Override
	public String toString() {
		return String.format("%s{vertices=%s opaque=%s}", this.getClass().getSimpleName(), vertices, opaque);
	}
}
