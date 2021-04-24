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
import com.google.common.hash.HashCode;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a vote on a vertex
 */
@Immutable
@SerializerId2("consensus.vote")
public final class Vote implements ConsensusEvent {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	private final BFTNode author;

	@JsonProperty("high_qc")
	@DsonOutput(Output.ALL)
	private final HighQC highQC;

	@JsonProperty("vote_data")
	@DsonOutput(Output.ALL)
	private final TimestampedVoteData voteData;

	@JsonProperty("signature")
	@DsonOutput(Output.ALL)
	private final ECDSASignature signature;

	private final Optional<ECDSASignature> timeoutSignature;

	@JsonCreator
	Vote(
		@JsonProperty("author") byte[] author,
		@JsonProperty("vote_data") TimestampedVoteData voteData,
		@JsonProperty("signature") ECDSASignature signature,
		@JsonProperty("high_qc") HighQC highQC,
		@JsonProperty("timeout_signature") ECDSASignature timeoutSignature
	) throws PublicKeyException {
		this(BFTNode.fromPublicKeyBytes(author), voteData, signature, highQC, Optional.ofNullable(timeoutSignature));
	}

	public Vote(
		BFTNode author,
		TimestampedVoteData voteData,
		ECDSASignature signature,
		HighQC highQC,
		Optional<ECDSASignature> timeoutSignature
	) {
		this.author = Objects.requireNonNull(author);
		this.voteData = Objects.requireNonNull(voteData);
		this.signature = Objects.requireNonNull(signature);
		this.highQC = Objects.requireNonNull(highQC);
		this.timeoutSignature = Objects.requireNonNull(timeoutSignature);
	}

	@Override
	public long getEpoch() {
		return voteData.getVoteData().getProposed().getLedgerHeader().getEpoch();
	}

	@Override
	public BFTNode getAuthor() {
		return author;
	}

	@Override
	public HighQC highQC() {
		return this.highQC;
	}

	@Override
	public View getView() {
		return getVoteData().getProposed().getView();
	}

	public VoteData getVoteData() {
		return voteData.getVoteData();
	}

	public HashCode getHashOfData(Hasher hasher) {
		return hasher.hash(voteData);
	}

	public long getTimestamp() {
		return voteData.getNodeTimestamp();
	}

	public ECDSASignature getSignature() {
		return this.signature;
	}

	public Optional<ECDSASignature> getTimeoutSignature() {
		return timeoutSignature;
	}

	public Vote withTimeoutSignature(ECDSASignature timeoutSignature) {
		return new Vote(
			this.author,
			this.voteData,
			this.signature,
			this.highQC,
			Optional.of(timeoutSignature)
		);
	}

	public boolean isTimeout() {
		return timeoutSignature.isPresent();
	}

	@JsonProperty("author")
	@DsonOutput(Output.ALL)
	private byte[] getSerializerAuthor() {
		return this.author == null ? null : this.author.getKey().getBytes();
	}

	@JsonProperty("timeout_signature")
	@DsonOutput(Output.ALL)
	private ECDSASignature getSerializerTimeoutSignature() {
		return this.timeoutSignature.orElse(null);
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s author=%s timeout?=%s %s}", getClass().getSimpleName(),
			getEpoch(), getView(), author, isTimeout(), highQC);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.author, this.voteData, this.signature, this.highQC, this.timeoutSignature);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof Vote) {
			Vote other = (Vote) o;
			return Objects.equals(this.author, other.author)
				&& Objects.equals(this.voteData, other.voteData)
				&& Objects.equals(this.signature, other.signature)
				&& Objects.equals(this.highQC, other.highQC)
				&& Objects.equals(this.timeoutSignature, other.timeoutSignature);
		}
		return false;
	}
}
