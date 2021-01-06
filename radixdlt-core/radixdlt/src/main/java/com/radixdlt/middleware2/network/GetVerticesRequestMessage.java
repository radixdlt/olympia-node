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
import com.google.common.hash.HashCode;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import org.radix.network.messaging.Message;

/**
 * RPC Message to get request for a vertex
 */
@SerializerId2("message.consensus.vertices_request")
public final class GetVerticesRequestMessage extends Message {
	@JsonProperty("vertexId")
	@DsonOutput(Output.ALL)
	private final HashCode vertexId;

	@JsonProperty("count")
	@DsonOutput(Output.ALL)
	private final int count;

	GetVerticesRequestMessage() {
		// Serializer only
		super(0);
		this.vertexId = null;
		this.count = 0;
	}

	GetVerticesRequestMessage(int magic, HashCode vertexId, int count) {
		super(magic);
		this.vertexId = Objects.requireNonNull(vertexId);
		this.count = count;
	}

	public HashCode getVertexId() {
		return vertexId;
	}

	public int getCount() {
		return count;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), vertexId);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GetVerticesRequestMessage that = (GetVerticesRequestMessage) o;
		return count == that.count
				&& Objects.equals(vertexId, that.vertexId)
				&& Objects.equals(getTimestamp(), that.getTimestamp())
				&& Objects.equals(getMagic(), that.getMagic());
	}

	@Override
	public int hashCode() {
		return Objects.hash(vertexId, count, getTimestamp(), getMagic());
	}
}
