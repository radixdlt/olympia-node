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
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * A request for bft vertices info
 */
public final class GetVerticesRequest {
	private final BFTNode sender;
	private final Hash vertexId;
	private final int count;

	public GetVerticesRequest(BFTNode sender, Hash vertexId, int count) {
		this.sender = Objects.requireNonNull(sender);
		this.vertexId = Objects.requireNonNull(vertexId);
		this.count = count;
	}

	public BFTNode getSender() {
		return sender;
	}

	public Hash getVertexId() {
		return vertexId;
	}

	public int getCount() {
		return count;
	}

	@Override
	public String toString() {
		return String.format("%s{id=%s count=%s}", this.getClass().getSimpleName(), this.vertexId, this.count);
	}
}
