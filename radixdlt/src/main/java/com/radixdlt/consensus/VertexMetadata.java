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

import com.radixdlt.consensus.bft.View;
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

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private View view;

	@JsonProperty("id")
	@DsonOutput(Output.ALL)
	private final Hash id;

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	@JsonProperty("is_end_of_epoch")
	@DsonOutput(Output.ALL)
	private final boolean isEndOfEpoch;

	VertexMetadata() {
		// Serializer only
		this.view = null;
		this.id = null;
		this.stateVersion = 0L;
		this.epoch = 0L;
		this.isEndOfEpoch = false;
	}

	public VertexMetadata(long epoch, View view, Hash id, long stateVersion, boolean isEndOfEpoch) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}

		if (stateVersion < 0) {
			throw new IllegalArgumentException("stateVersion must be >= 0");
		}

		this.epoch = epoch;
		this.stateVersion = stateVersion;
		this.view = view;
		this.id = id;
		this.isEndOfEpoch = isEndOfEpoch;
	}

	public static VertexMetadata ofGenesisAncestor() {
		return new VertexMetadata(0, View.genesis(), Hash.ZERO_HASH, 0, true);
	}

	public static VertexMetadata ofVertex(Vertex vertex, boolean isEndOfEpoch) {
		final VertexMetadata parent = vertex.getQC().getProposed();
		final long parentStateVersion = parent.getStateVersion();

		final boolean isLastToBeCommitted = !parent.isEndOfEpoch && isEndOfEpoch;

		final int versionIncrement = vertex.getAtom() != null || isLastToBeCommitted ? 1 : 0;
		final long newStateVersion = parentStateVersion + versionIncrement;
		return new VertexMetadata(vertex.getEpoch(), vertex.getView(), vertex.getId(), newStateVersion, isEndOfEpoch);
	}

	public boolean isEndOfEpoch() {
		return isEndOfEpoch;
	}

	public long getEpoch() {
		return epoch;
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
		return Objects.hash(this.view, this.id, this.stateVersion, this.isEndOfEpoch, this.epoch);
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
				&& this.stateVersion == other.stateVersion
				&& this.isEndOfEpoch == other.isEndOfEpoch
				&& this.epoch == other.epoch;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s isEndOfEpoch=%s stateVersion=%s}",
			getClass().getSimpleName(), this.epoch, this.view, this.isEndOfEpoch, this.stateVersion
		);
	}
}