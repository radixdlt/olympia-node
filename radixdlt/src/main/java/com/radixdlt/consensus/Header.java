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

/**
 * The header which gets voted upon by consensus.
 */
@Immutable
@SerializerId2("consensus.header")
public final class Header {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private View view;

	@JsonProperty("vertex_id")
	@DsonOutput(Output.ALL)
	private final Hash vertexId;

	@JsonProperty("ledger_state")
	@DsonOutput(Output.ALL)
	private final LedgerState ledgerState;

	Header() {
		// Serializer only
		this.view = null;
		this.epoch = 0L;
		this.vertexId = null;
		this.ledgerState = null;
	}

	// TODO: Move command output to a more opaque data structure
	public Header(
		long epoch, // consensus data
		View view, // consensus data
		Hash vertexId, // consensus data
		LedgerState ledgerState
	) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}

		this.epoch = epoch;
		this.view = view;
		this.vertexId = vertexId;
		this.ledgerState = ledgerState;
	}

	public static Header ofGenesisAncestor(LedgerState ledgerState) {
		return new Header(
			0,
			View.genesis(),
			Hash.ZERO_HASH,
			ledgerState
		);
	}

	public static Header ofGenesisVertex(Vertex vertex) {
		return new Header(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(),
			LedgerState.create(
				vertex.getEpoch(),
				vertex.getQC().getParent().getLedgerState().getStateVersion(),
				Hash.ZERO_HASH,
				0L,
				false
			)
		);
	}

	public static Header ofVertex(Vertex vertex, LedgerState ledgerState) {
		return new Header(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(),
			ledgerState
		);
	}

	public LedgerState getLedgerState() {
		return ledgerState;
	}

	public long getEpoch() {
		return epoch;
	}

	public View getView() {
		return view;
	}

	public Hash getVertexId() {
		return vertexId;
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
		return Objects.hash(this.epoch, this.view, this.vertexId, this.ledgerState);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Header) {
			Header other = (Header) o;
			return
				Objects.equals(this.view, other.view)
				&& this.epoch == other.epoch
				&& Objects.equals(this.vertexId, other.vertexId)
				&& Objects.equals(this.ledgerState, other.ledgerState);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s out=%s}",
			getClass().getSimpleName(), this.epoch, this.view, this.ledgerState
		);
	}
}