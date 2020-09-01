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

	@JsonProperty("command_output")
	@DsonOutput(Output.ALL)
	private final CommandOutput commandOutput;

	VertexMetadata() {
		// Serializer only
		this.view = null;
		this.id = null;
		this.epoch = 0L;
		this.commandOutput = null;
	}

	// TODO: Move executor data to a more opaque data structure
	public VertexMetadata(
		long epoch, // consensus data
		View view, // consensus data
		Hash id, // consensus data
		CommandOutput commandOutput
	) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}

		this.commandOutput = commandOutput;
		this.epoch = epoch;
		this.view = view;
		this.id = id;
	}

	public static VertexMetadata ofGenesisAncestor(CommandOutput commandOutput) {
		return new VertexMetadata(
			0,
			View.genesis(),
			Hash.ZERO_HASH, commandOutput
		);
	}

	public static VertexMetadata ofGenesisVertex(Vertex vertex) {
		return new VertexMetadata(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(),
			CommandOutput.create(
				vertex.getQC().getParent().getPreparedCommand().getStateVersion(),
				0L,
				false
			)
		);
	}

	public static VertexMetadata ofVertex(Vertex vertex, CommandOutput commandOutput) {
		return new VertexMetadata(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(), commandOutput
		);
	}

	public CommandOutput getPreparedCommand() {
		return commandOutput;
	}

	public long getEpoch() {
		return epoch;
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
		return Objects.hash(this.epoch, this.view, this.id, this.commandOutput);
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
				&& this.epoch == other.epoch
				&& Objects.equals(this.commandOutput, other.commandOutput);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s prepared=%s}",
			getClass().getSimpleName(), this.epoch, this.view, this.commandOutput
		);
	}
}