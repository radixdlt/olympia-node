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

public final class LocalGetVerticesRequest {
	private final Hash vertexId;
	private final int count;
	private final BFTNode to;

	public LocalGetVerticesRequest(BFTNode to, Hash vertexId, int count) {
		this.to = to;
		this.vertexId = vertexId;
		this.count = count;
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertexId, to, count);
	}

	@Override
	public boolean equals(Object o)  {
		if (!(o instanceof LocalGetVerticesRequest)) {
			return false;
		}
		LocalGetVerticesRequest other = (LocalGetVerticesRequest) o;

		return Objects.equals(other.vertexId, this.vertexId)
			&& Objects.equals(other.to, this.to)
			&& other.count == this.count;
	}
}
