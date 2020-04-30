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

import com.radixdlt.crypto.Hash;
import java.util.function.Consumer;

/**
 * An RPC request to retrieve a given vertex
 */
public final class GetVertexRequest {
	private final Hash vertexId;
	private final Consumer<Vertex> responder;

	public GetVertexRequest(Hash vertexId, Consumer<Vertex> responder) {
		this.vertexId = vertexId;
		this.responder = responder;
	}

	public Consumer<Vertex> getResponder() {
		return responder;
	}

	public Hash getVertexId() {
		return vertexId;
	}

	@Override
	public String toString() {
		return String.format("%s{vertexId=%s}",
			getClass().getSimpleName(), vertexId.toString().substring(0, 6));
	}
}
