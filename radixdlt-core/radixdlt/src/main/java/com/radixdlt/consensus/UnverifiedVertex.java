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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Vertex in a Vertex graph
 */
@Immutable
@SerializerId2("consensus.vertex")
public final class UnverifiedVertex {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate qc;

	private final View view;

	@JsonProperty("command")
	@DsonOutput(Output.ALL)
	private final Command command;

	@JsonCreator
	UnverifiedVertex(
		@JsonProperty("qc") QuorumCertificate qc,
		@JsonProperty("view") Long viewId,
		@JsonProperty("command") Command command
	) {
		this(qc, viewId != null ? View.of(viewId) : null, command);
	}

	public UnverifiedVertex(QuorumCertificate qc, View view, Command command) {
		this.qc = Objects.requireNonNull(qc);
		this.view = Objects.requireNonNull(view);
		this.command = command;
	}

	public static UnverifiedVertex createGenesis(LedgerHeader ledgerHeader) {
		BFTHeader header = BFTHeader.ofGenesisAncestor(ledgerHeader);
		final VoteData voteData = new VoteData(header, header, header);
		final QuorumCertificate qc = new QuorumCertificate(voteData, new TimestampedECDSASignatures());
		return new UnverifiedVertex(qc, View.genesis(), null);
	}

	public static UnverifiedVertex createVertex(QuorumCertificate qc, View view, Command command) {
		Objects.requireNonNull(qc);

		if (view.number() == 0) {
			throw new IllegalArgumentException("Only genesis can have view 0.");
		}

		return new UnverifiedVertex(qc, view, command);
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public View getView() {
		return view;
	}

	public Command getCommand() {
		return command;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@Override
	public String toString() {
		return String.format("Vertex{view=%s, qc=%s, cmd=%s}", view, qc, command);
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, view, command);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UnverifiedVertex)) {
			return false;
		}

		UnverifiedVertex v = (UnverifiedVertex) o;
		return Objects.equals(v.view, this.view)
			&& Objects.equals(v.command, this.command)
			&& Objects.equals(v.qc, this.qc);
	}
}
