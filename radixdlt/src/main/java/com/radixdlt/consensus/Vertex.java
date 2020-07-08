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
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.Hash;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.DsonOutput;
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

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate qc;

	private View view;

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final ClientAtom atom;

	private final transient Supplier<Hash> cachedHash = Suppliers.memoize(this::doGetHash);

	Vertex() {
		// Serializer only
		this.qc = null;
		this.epoch = 0L;
		this.view = null;
		this.atom = null;
	}

	public Vertex(long epoch, QuorumCertificate qc, View view, ClientAtom atom) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}

		this.epoch = epoch;
		this.qc = Objects.requireNonNull(qc);
		this.view = Objects.requireNonNull(view);
		this.atom = atom;
	}

	public static Vertex createGenesis() {
		return createGenesis(VertexMetadata.ofGenesisAncestor());
	}

	public static Vertex createGenesis(VertexMetadata ancestorMetadata) {
		Objects.requireNonNull(ancestorMetadata);
		final VoteData voteData = new VoteData(ancestorMetadata, ancestorMetadata, ancestorMetadata);
		final QuorumCertificate qc = new QuorumCertificate(voteData, new ECDSASignatures());
		return new Vertex(ancestorMetadata.getEpoch() + 1, qc, View.genesis(), null);
	}

	public static Vertex createVertex(QuorumCertificate qc, View view, ClientAtom reAtom) {
		Objects.requireNonNull(qc);

		if (view.number() == 0) {
			throw new IllegalArgumentException("Only genesis can have view 0.");
		}

		return new Vertex(qc.getProposed().getEpoch(), qc, view, reAtom);
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
		return qc.getProposed().getId();
	}

	public VertexMetadata getGrandParentMetadata() {
		return qc.getParent();
	}

	public VertexMetadata getParentMetadata() {
		return qc.getProposed();
	}

	public QuorumCertificate getQC() {
		return qc;
	}

	public boolean hasDirectParent() {
		return this.view.number() == this.getParentMetadata().getView().number() + 1;
	}

	public long getEpoch() {
		return epoch;
	}

	public View getView() {
		return view;
	}

	public ClientAtom getAtom() {
		return atom;
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
		return String.format("Vertex{epoch=%s view=%s, qc=%s, atom=%s}", epoch, view, qc, atom == null ? null : atom.getAID());
	}

	@Override
	public int hashCode() {
		return Objects.hash(qc, view, atom, epoch);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Vertex)) {
			return false;
		}

		Vertex v = (Vertex) o;
		return Objects.equals(v.view, this.view)
			&& Objects.equals(v.atom, this.atom)
			&& Objects.equals(v.qc, this.qc)
			&& v.epoch == this.epoch;
	}
}
