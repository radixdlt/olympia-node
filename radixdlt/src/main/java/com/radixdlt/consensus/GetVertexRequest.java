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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

public final class GetVertexRequest {
	private final Hash vertexId;
	private final ECPublicKey requestor;

	public GetVertexRequest(Hash vertexId, ECPublicKey requestor) {
		this.vertexId = vertexId;
		this.requestor = requestor;
	}

	public Hash getVertexId() {
		return vertexId;
	}

	public ECPublicKey getRequestor() {
		return requestor;
	}

	@Override
	public String toString() {
		return String.format("%s{vertexId=%s requestor=%s}",
			getClass().getSimpleName(), vertexId.toString().substring(0, 6), requestor.euid().toString().substring(0, 6));
	}
}
