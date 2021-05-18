/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a proposal made by a leader in a round of consensus
 */
@SerializerId2("consensus.proposal")
@Immutable // author cannot be but is effectively final because of serializer
public final class Proposal implements ConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("vertex")
	@DsonOutput(Output.ALL)
	private final UnverifiedVertex vertex;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	@JsonProperty("committedQC")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate committedQC;

	@JsonProperty("highestTC")
	@DsonOutput(Output.ALL)
	private final TimeoutCertificate highestTC;

	@JsonCreator
	Proposal(
		@JsonProperty("vertex") UnverifiedVertex vertex,
		@JsonProperty("committedQC") QuorumCertificate committedQC,
		@JsonProperty("signature") ECDSASignature signature,
		@JsonProperty("highestTC") TimeoutCertificate highestTC
	) {
		this(vertex, committedQC, signature, Optional.ofNullable(highestTC));
	}

	public Proposal(
		UnverifiedVertex vertex,
		QuorumCertificate committedQC,
		ECDSASignature signature,
		Optional<TimeoutCertificate> highestTC
	) {
		this.vertex = Objects.requireNonNull(vertex);
		this.committedQC = committedQC;
		this.signature = Objects.requireNonNull(signature);

		this.highestTC = // only relevant if it's for a higher view than QC
			highestTC.filter(tc -> tc.getView().gt(vertex.getQC().getView())).orElse(null);
	}

	@Override
	public long getEpoch() {
		return vertex.getQC().getProposed().getLedgerHeader().getEpoch();
	}

	@Override
	public View getView() {
		return vertex.getView();
	}

	@Override
	public HighQC highQC() {
		return HighQC.from(vertex.getQC(), committedQC, Optional.ofNullable(highestTC));
	}

	@Override
	public BFTNode getAuthor() {
		return vertex.getProposer();
	}

	public UnverifiedVertex getVertex() {
		return vertex;
	}

	public ECDSASignature getSignature() {
		return signature;
	}

	@Override
	public String toString() {
		return String.format("%s{vertex=%s author=%s tc=%s}", getClass().getSimpleName(), vertex, getAuthor(), highestTC);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.vertex, this.signature, this.committedQC, this.highestTC);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Proposal) {
			Proposal other = (Proposal) o;
			return Objects.equals(this.vertex, other.vertex)
				&& Objects.equals(this.signature, other.signature)
				&& Objects.equals(this.committedQC, other.committedQC)
				&& Objects.equals(this.highestTC, other.highestTC);
		}
		return false;
	}
}
