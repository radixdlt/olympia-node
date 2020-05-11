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
import com.google.common.base.Suppliers;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.LedgerAtom.CMAtomConversionException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

import java.util.Objects;

import java.util.function.Supplier;
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

	private View view;
	private LedgerAtom reAtom;

	private final transient Supplier<Hash> cachedHash = Suppliers.memoize(this::doGetHash);

	Vertex() {
		// Serializer only
		this.qc = null;
		this.view = null;
		this.reAtom = null;
	}

	public Vertex(QuorumCertificate qc, View view, LedgerAtom reAtom) {
		this.qc = qc;
		this.view = Objects.requireNonNull(view);
		this.reAtom = reAtom;
	}


	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAtom() {
		try {
			return this.reAtom == null ? null : DefaultSerialization.getInstance().toDson(reAtom.getRaw(), Output.WIRE);
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to serialize " + this.reAtom);
		}
	}

	@JsonProperty("atom")
	private void setSerializerAtom(byte[] atomBytes) {
		try {
			Atom rawAtom = atomBytes == null ? null : DefaultSerialization.getInstance().fromDson(atomBytes, Atom.class);
			this.reAtom = rawAtom == null ? null : LedgerAtom.convert(rawAtom);
		} catch (SerializationException | CMAtomConversionException e) {
			throw new IllegalStateException("Failed to deserialize atomBytes");
		}
	}

	public static Vertex createGenesis(LedgerAtom atom) {
		return new Vertex(null, View.of(0), atom);
	}

	public static Vertex createVertex(QuorumCertificate qc, View view, LedgerAtom reAtom) {
		Objects.requireNonNull(qc);

		if (view.number() == 0) {
			throw new IllegalArgumentException("Only genesis can have view 0.");
		}

		return new Vertex(qc, view, reAtom);
	}

	private Hash doGetHash() {
		try {
			return Hash.of(DefaultSerialization.getInstance().toDson(this, Output.HASH));
		} catch (Exception e) {
			throw new IllegalStateException("Error generating hash: " + e, e);
		}
	}

	public Hash getId() {
		return this.cachedHash.get();
	}

	public Hash getParentId() {
		return qc == null ? null : qc.getProposed().getId();
	}

	public View getParentView() {
		return qc == null ? View.genesis() : qc.getView();
	}

	public View getGrandParentView() {
		if (qc == null || qc.getParent() == null) {
			return View.genesis();
		}
		return qc.getParent().getView();
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public boolean hasDirectParent() {
		return this.view.number() == this.getParentView().number() + 1;
	}

	public View getView() {
		return view;
	}

	// TODO: This is a hack. Fix when we can serialize SimpleRadixEngineAtom
	public LedgerAtom getAtom() {
		return reAtom;
	}

	public boolean isGenesis() {
		return this.view.isGenesis();
	}

	@JsonProperty("id")
	@DsonOutput(Output.API)
	private Hash getSerializerId() {
		return getId();
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
	public String toString() {
		return String.format("Vertex{view=%s, qc=%s, atom=%s}", view, qc, reAtom == null ? null : reAtom.getAID());
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, view, reAtom == null ? null : reAtom.getAID());
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return Objects.equals(v.view, view)
			&& Objects.equals(v.reAtom == null ? null : v.reAtom.getAID(), this.reAtom == null ? null : this.reAtom.getAID())
			&& Objects.equals(v.qc, this.qc);
	}
}
