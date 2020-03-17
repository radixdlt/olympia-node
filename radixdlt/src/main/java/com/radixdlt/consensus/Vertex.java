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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.common.Atom;
import com.radixdlt.common.EUID;
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
public final class Vertex {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate qc;

	private Round round;

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final Atom atom;

	Vertex() {
		// Serializer only
		this.qc = null;
		this.round = null;
		this.atom = null;
	}

	private Vertex(QuorumCertificate qc, Round round, Atom atom) {
		this.qc = qc;
		this.round = round;
		this.atom = atom;
	}

	public static Vertex createGenesis(Atom atom) {
		return new Vertex(null, Round.of(0), atom);
	}

	public static Vertex createVertex(QuorumCertificate qc, Round round, Atom atom) {
		Objects.requireNonNull(qc);
		Objects.requireNonNull(round);

		if (round.number() == 0) {
			throw new IllegalArgumentException("Only genesis can have round 0.");
		}

		return new Vertex(qc, round, atom);
	}

	// TODO: Replace with a better type over EUID wrapping hashCode()
	public EUID getId() {
		return new EUID(this.hashCode());
	}

	public EUID getParentId() {
		return qc == null ? null : qc.getVertexMetadata().getId();
	}

	public Round getParentRound() {
		return qc == null ? Round.of(0) : qc.getRound();
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public Round getRound() {
		return round;
	}

	public Atom getAtom() {
		return atom;
	}

	@JsonProperty("round")
	@DsonOutput(Output.ALL)
	private Long getSerializerRound() {
		return this.round == null ? null : this.round.number();
	}

	@JsonProperty("round")
	private void setSerializerRound(Long number) {
		this.round = number == null ? null : Round.of(number);
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, round, atom);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return Objects.equals(v.round, round)
			&& Objects.equals(v.atom, this.atom)
			&& Objects.equals(v.qc, this.qc);
	}
}
