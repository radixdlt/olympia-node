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
import com.radixdlt.consensus.Vertex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import java.util.List;
import java.util.Objects;
import org.radix.network.messaging.Message;

/**
 * RPC Response message for GetVertex call
 */
@SerializerId2("message.consensus.vertices_response")
public final class GetVerticesResponseMessage extends Message {
	@JsonProperty("vertices")
	@DsonOutput(Output.ALL)
	private final List<Vertex> vertices;

	GetVerticesResponseMessage() {
		// Serializer only
		super(0);
		this.vertices = null;
	}

	GetVerticesResponseMessage(int magic, List<Vertex> vertices) {
		super(magic);
		this.vertices = Objects.requireNonNull(vertices);
	}

	public List<Vertex> getVertices() {
		return vertices;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), vertices);
	}
}
