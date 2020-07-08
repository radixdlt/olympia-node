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

import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.crypto.Hash;
import java.util.Objects;

/**
 * An error response to the GetVertices call
 */
public final class GetVerticesErrorResponse {
	private final Hash vertexId;
	private final Object opaque;
	private final QuorumCertificate highestQC;
	private final QuorumCertificate highestCommittedQC;

	public GetVerticesErrorResponse(Hash vertexId, QuorumCertificate highestQC, QuorumCertificate highestCommittedQC, Object opaque) {
		this.vertexId = Objects.requireNonNull(vertexId);
		this.highestQC = Objects.requireNonNull(highestQC);
		this.highestCommittedQC = Objects.requireNonNull(highestCommittedQC);
		this.opaque = opaque;
	}

	public Hash getVertexId() {
		return vertexId;
	}

	public Object getOpaque() {
		return opaque;
	}

	public QuorumCertificate getHighestQC() {
		return highestQC;
	}

	public QuorumCertificate getHighestCommittedQC() {
		return highestCommittedQC;
	}

	@Override
	public String toString() {
		return String.format("%s{highQC=%s highCommittedQC=%s opaque=%s}", this.getClass().getSimpleName(), highestQC, highestCommittedQC, opaque);
	}
}
