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
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a new-view message from the pacemaker
 */
@SerializerId2("consensus.newview")
@Immutable // view and author cannot be but are effectively final because of serializer
public final class NewView {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("qc")
	@DsonOutput(Output.ALL)
	private QuorumCertificate qc;

	private ECPublicKey author;

	private View view;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature; // may be null if not signed (e.g. for genesis)

	NewView() {
		// Serializer only
		this.author = null;
		this.view = View.of(Long.MAX_VALUE);
		this.signature = null;
	}

	public NewView(ECPublicKey author, View view, QuorumCertificate qc, ECDSASignature signature) {
		this.author = Objects.requireNonNull(author);
		this.view = Objects.requireNonNull(view);
		this.qc = qc;
		this.signature = signature;
	}

	public QuorumCertificate getQc() {
		return qc;
	}

	public ECPublicKey getAuthor() {
		return author;
	}

	public View getView() {
		return view;
	}

	public Optional<ECDSASignature> getSignature() {
		return Optional.ofNullable(this.signature);
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

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getBytes();
	}
	@JsonProperty("author")
	private void setSerializerAuthor(byte[] author) throws CryptoException {
		this.author = (author == null) ? null : new ECPublicKey(author);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		NewView newView = (NewView) o;
		return Objects.equals(author, newView.author)
			&& Objects.equals(view, newView.view)
			&& Objects.equals(qc, newView.qc)
			&& Objects.equals(signature, newView.signature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(author, view, qc, signature);
	}

	@Override
	public String toString() {
		return String.format("%s{view=%s qc=%s author=%s}", getClass().getSimpleName(), view, qc, author.getUID().toString().substring(0, 6));
	}
}
