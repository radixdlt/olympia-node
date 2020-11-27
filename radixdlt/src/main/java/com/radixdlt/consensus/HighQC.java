/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * Current state of synchronisation for sending node.
 */
@Immutable
@SerializerId2("consensus.high_qc")
public final class HighQC {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("highest_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate highestQC;

	@JsonProperty("committed_qc")
	@DsonOutput(Output.ALL)
	private final QuorumCertificate highestCommittedQC;

	@JsonProperty("highest_tc")
	@DsonOutput(Output.ALL)
	private final Optional<TimeoutCertificate> highestTC;

	@JsonCreator
	private static HighQC serializerCreate(
		@JsonProperty("highest_qc") QuorumCertificate highestQC,
		@JsonProperty("committed_qc") QuorumCertificate highestCommittedQC,
		@JsonProperty("highest_tc") Optional<TimeoutCertificate> highestTC
	) {
		return new HighQC(highestQC, highestCommittedQC, highestTC);
	}

	private HighQC(
			QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC,
			Optional<TimeoutCertificate> highestTC) {
		this.highestQC = Objects.requireNonNull(highestQC);
		// Don't include separate committedQC if it is the same as highQC.
		// This significantly reduces the serialised size of the object.
		if (highestCommittedQC == null || highestQC.equals(highestCommittedQC)) {
			this.highestCommittedQC = null;
		} else {
			this.highestCommittedQC = highestCommittedQC;
		}

		this.highestTC = // only relevant if it's for a higher view than QC
				highestTC.filter(tc -> tc.getView().gt(highestQC.getView()));
	}

	/**
	 * Creates a {@link HighQC} from the specified QCs.
	 * <p>
	 * Note that highestCommittedQC->committed needs to be an ancestor of
	 * highestQC->proposed, but highestCommittedQC->proposed does not need
	 * to be an ancestor of highestQC->proposed.
	 *
	 * @param highestQC The highest QC we have seen
	 * @param highestCommittedQC The highest QC we have committed
	 * @param highestTC The highest timeout certificate
	 * @return A new {@link HighQC}
	 */
	public static HighQC from(
			QuorumCertificate highestQC,
			QuorumCertificate highestCommittedQC,
			Optional<TimeoutCertificate> highestTC) {
		return new HighQC(highestQC, Objects.requireNonNull(highestCommittedQC), highestTC);
	}

	public Optional<TimeoutCertificate> highestTC() {
		return this.highestTC;
	}

	public QuorumCertificate highestQC() {
		return this.highestQC;
	}

	public View getHighestView() {
		if (this.highestTC.isPresent()
				&& this.highestTC.get().getView().gt(this.highestQC.getView())) {
			return this.highestTC.get().getView();
		} else {
			return this.highestQC.getView();
		}
	}

	public QuorumCertificate highestCommittedQC() {
		return this.highestCommittedQC == null ? this.highestQC : this.highestCommittedQC;
	}

	public VerifiedLedgerHeaderAndProof proof() {
		return this.highestCommittedQC().getCommittedAndLedgerStateProof().orElseThrow().getSecond();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.highestQC, this.highestCommittedQC, this.highestTC);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof HighQC) {
			HighQC that = (HighQC) o;
			return Objects.equals(this.highestCommittedQC, that.highestCommittedQC)
				&& Objects.equals(this.highestQC, that.highestQC)
				&& Objects.equals(this.highestTC, that.highestTC);
		}
		return false;
	}

	@Override
	public String toString() {
		String highestCommittedString = (this.highestCommittedQC == null) ? "<same>" : this.highestCommittedQC.toString();
		return String.format("%s[highest=%s, highestCommitted=%s, highestTC=%s]",
			getClass().getSimpleName(), this.highestQC, highestCommittedString, highestTC);
	}
}
