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

import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.bft.BFTNode;
import java.util.Objects;

/**
 * An error response to the GetVertices call
 */
public final class GetVerticesErrorResponse {
	private final BFTNode sender;
	private final HighQC highQC;
	private final GetVerticesRequest failedRequest;

	public GetVerticesErrorResponse(BFTNode sender, HighQC highQC, GetVerticesRequest failedRequest) {
		this.sender = Objects.requireNonNull(sender);
		this.highQC = Objects.requireNonNull(highQC);
		this.failedRequest = Objects.requireNonNull(failedRequest);
	}

	public BFTNode getSender() {
		return this.sender;
	}

	public HighQC highQC() {
		return this.highQC;
	}

	public GetVerticesRequest failedRequest() {
		return this.failedRequest;
	}

	@Override
	public String toString() {
		return String.format("%s{%s->%s (%s)}", this.getClass().getSimpleName(), this.sender, this.highQC, this.failedRequest);
	}
}
