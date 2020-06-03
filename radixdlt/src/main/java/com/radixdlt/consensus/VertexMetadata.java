/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.radixdlt.crypto.Hash;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@Immutable
@SerializerId2("consensus.vertex_metadata")
public final class VertexMetadata {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private View view;

	@JsonProperty("id")
	@DsonOutput(Output.ALL)
	private final Hash id;

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	VertexMetadata() {
		// Serializer only
		this.view = null;
		this.id = null;
		this.stateVersion = 0L;
	}

	public VertexMetadata(View view, Hash id, long stateVersion) {
		if (stateVersion < 0) {
			throw new IllegalArgumentException("stateVersion must be >= 0");
		}

		this.stateVersion = stateVersion;
		this.view = view;
		this.id = id;
	}

	public static VertexMetadata ofVertex(Vertex vertex) {
		final long parentStateVersion;
		if (vertex.isGenesis()) {
			throw new IllegalArgumentException("Must use normal constructor for genesis.");
		} else {
			final VertexMetadata parent = vertex.getQC().getProposed();
			parentStateVersion = parent.getStateVersion();
		}

		final int versionIncrement = vertex.getAtom() != null ? 1 : 0;
		final long newStateVersion = parentStateVersion + versionIncrement;
		return new VertexMetadata(vertex.getView(), vertex.getId(), newStateVersion);
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public View getView() {
		return view;
	}

	public Hash getId() {
		return id;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("view")
	private void setSerializerView(Long number) {
		this.view = number == null ? null : View.of(number);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.view, this.id, this.stateVersion);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof VertexMetadata) {
			VertexMetadata other = (VertexMetadata) o;
			return
				Objects.equals(this.view, other.view)
				&& Objects.equals(this.id, other.id)
				&& this.stateVersion == other.stateVersion;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s stateVersion=%s}", getClass().getSimpleName(), view, stateVersion);
	}
}