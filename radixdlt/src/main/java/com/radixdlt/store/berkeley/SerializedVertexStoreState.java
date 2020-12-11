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

package com.radixdlt.store.berkeley;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.TimeoutCertificate;
import com.radixdlt.consensus.UnverifiedVertex;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;

/**
 * Vertex Store State version which can be serialized.
 */
@SerializerId2("store.vertices")
public final class SerializedVertexStoreState {

	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("root")
	@DsonOutput(Output.ALL)
	private final UnverifiedVertex root;

	@JsonProperty("vertices")
	@DsonOutput(Output.ALL)
	private final ImmutableList<UnverifiedVertex> vertices;

	@JsonProperty("high_qc")
	@DsonOutput(Output.ALL)
	private final HighQC highQC;

	@JsonProperty("highest_tc")
	@DsonOutput(Output.ALL)
	private final TimeoutCertificate highestTC;

	@JsonCreator
	public SerializedVertexStoreState(
		@JsonProperty("high_qc") HighQC highQC,
		@JsonProperty("root") UnverifiedVertex root,
		@JsonProperty("vertices") ImmutableList<UnverifiedVertex> vertices,
		@JsonProperty("highest_tc") TimeoutCertificate highestTC
	) {
		this.root = Objects.requireNonNull(root);
		this.vertices = Objects.requireNonNull(vertices);
		this.highQC = Objects.requireNonNull(highQC);
		this.highestTC = highestTC;
	}

	public UnverifiedVertex getRoot() {
		return root;
	}

	public ImmutableList<UnverifiedVertex> getVertices() {
		return vertices;
	}

	public HighQC getHighQC() {
		return highQC;
	}

	public Optional<TimeoutCertificate> getHighestTC() {
		return Optional.ofNullable(highestTC);
	}

	@Override
	public int hashCode() {
		return Objects.hash(root, vertices, highQC, highestTC);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SerializedVertexStoreState)) {
			return false;
		}

		SerializedVertexStoreState other = (SerializedVertexStoreState) o;
		return Objects.equals(this.root, other.root)
			&& Objects.equals(this.vertices, other.vertices)
			&& Objects.equals(this.highQC, other.highQC)
			&& Objects.equals(this.highestTC, other.highestTC);
	}

	@Override
	public String toString() {
		return String.format("%s{highQC=%s root=%s vertices=%s highestTc=%s}",
			this.getClass().getSimpleName(),
			this.root,
			this.vertices,
			this.highestTC
		);
	}
}
