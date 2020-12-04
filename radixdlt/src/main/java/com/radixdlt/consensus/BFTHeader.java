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

import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.View;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * The bft header which gets voted upon by consensus.
 */
@Immutable
@SerializerId2("consensus.bft_header")
public final class BFTHeader {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private View view;

	@JsonProperty("vertex_id")
	@DsonOutput(Output.ALL)
	private final HashCode vertexId;

	// TODO(luk): rename this json property?
	@JsonProperty("ledger_state")
	@DsonOutput(Output.ALL)
	private final LedgerHeader ledgerHeader;

	BFTHeader() {
		// Serializer only
		this.view = null;
		this.vertexId = null;
		this.ledgerHeader = null;
	}

	// TODO: Move command output to a more opaque data structure
	public BFTHeader(
		View view, // consensus data
		HashCode vertexId, // consensus data
		LedgerHeader ledgerHeader
	) {
		this.view = view;
		this.vertexId = vertexId;
		this.ledgerHeader = ledgerHeader;
	}

	public static BFTHeader ofGenesisAncestor(LedgerHeader ledgerHeader) {
		return new BFTHeader(
			View.genesis(),
			HashUtils.zero256(),
			ledgerHeader
		);
	}

	public LedgerHeader getLedgerHeader() {
		return ledgerHeader;
	}

	public View getView() {
		return view;
	}

	public HashCode getVertexId() {
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
		return Objects.hash(this.vertexId, this.view, this.ledgerHeader);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof BFTHeader) {
			BFTHeader other = (BFTHeader) o;
			return
				Objects.equals(this.view, other.view)
				&& Objects.equals(this.vertexId, other.vertexId)
				&& Objects.equals(this.ledgerHeader, other.ledgerHeader);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s ledger=%s}",
			getClass().getSimpleName(), this.view, this.ledgerHeader
		);
	}
}