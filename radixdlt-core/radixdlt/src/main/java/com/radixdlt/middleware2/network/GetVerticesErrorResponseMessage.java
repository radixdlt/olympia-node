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

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import org.radix.network.messaging.Message;

@SerializerId2("message.consensus.vertices_error_response")
public final class GetVerticesErrorResponseMessage extends Message {
	@JsonProperty("high_qc")
	@DsonOutput(Output.ALL)
	private HighQC highQC;

	@JsonProperty("failing_request")
	@DsonOutput(Output.ALL)
	private GetVerticesRequestMessage failingRequest;

	GetVerticesErrorResponseMessage() {
		// Serializer only
		super(0);
		this.highQC = null;
	}

	GetVerticesErrorResponseMessage(int magic, HighQC highQC, GetVerticesRequestMessage failiingRequest) {
		super(magic);
		this.highQC = Objects.requireNonNull(highQC);
		this.failingRequest = Objects.requireNonNull(failiingRequest);
	}

	public HighQC highQC() {
		return this.highQC;
	}

	public GetVerticesRequestMessage failingRequest() {
		return this.failingRequest;
	}

	@Override
	public String toString() {
		return String.format("%s{%s (%s)}", getClass().getSimpleName(), this.highQC, this.failingRequest);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GetVerticesErrorResponseMessage that = (GetVerticesErrorResponseMessage) o;
		return Objects.equals(this.highQC, that.highQC)
				&& Objects.equals(this.failingRequest, that.failingRequest)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.highQC, this.failingRequest, getTimestamp(), getMagic());
	}
}
