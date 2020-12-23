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

package com.radixdlt.consensus.sync;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VerifiedVertex;
import java.util.List;
import java.util.Objects;

/**
 * An RPC response
 */
public final class GetVerticesResponse {
	private final List<VerifiedVertex> vertices;
	private final BFTNode sender;

	public GetVerticesResponse(BFTNode sender, List<VerifiedVertex> vertices) {
		this.sender = Objects.requireNonNull(sender);
		this.vertices = Objects.requireNonNull(vertices);
	}

	public BFTNode getSender() {
		return sender;
	}

	public List<VerifiedVertex> getVertices() {
		return vertices;
	}

	@Override
	public String toString() {
		return String.format("%s{vertices=%s}", this.getClass().getSimpleName(), vertices);
	}
}
