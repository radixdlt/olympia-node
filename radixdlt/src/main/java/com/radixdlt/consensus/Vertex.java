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
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Vertex in the BFT Chain
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

	private View view;

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final Atom atom;

	Vertex() {
		// Serializer only
		this.qc = null;
		this.view = null;
		this.atom = null;
	}

	public Vertex(QuorumCertificate qc, View view, Atom atom) {
		this.qc = qc;
		this.view = Objects.requireNonNull(view);
		this.atom = atom;
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public View getView() {
		return view;
	}

	public AID getAID() {
		return atom.getAID();
	}

	public Atom getAtom() {
		return atom;
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("view")
	private void setSerializerView(Long number) {
		this.view = number == null ? null : View.of(number.longValue());
	}

	@Override
	public String toString() {
		return "Vertex{" +
			"view=" + view +
			", atom=" + atom +
			", qc=" + qc +
			'}';
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, view, atom);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return Objects.equals(v.view, view)
			&& Objects.equals(v.atom, this.atom)
			&& Objects.equals(v.qc, this.qc);
	}
}
