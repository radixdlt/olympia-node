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

import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.utils.Pair;
import java.util.Objects;

/**
 * Configuration for a bft instance which should be shared by all validators in the bft.
 */
public final class BFTConfiguration {
	private final BFTValidatorSet validatorSet;
	private final VerifiedVertex genesisVertex;
	private final QuorumCertificate genesisQC;
	private final VerifiedLedgerHeaderAndProof genesisHeader;

	public BFTConfiguration(BFTValidatorSet validatorSet, VerifiedVertex genesisVertex, QuorumCertificate genesisQC) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.genesisVertex = Objects.requireNonNull(genesisVertex);
		this.genesisQC = Objects.requireNonNull(genesisQC);
		this.genesisHeader = this.genesisQC.getCommittedAndLedgerStateProof().map(Pair::getSecond)
			.orElseThrow(() -> new IllegalArgumentException("genesisQC must be committed."));
	}

	public BFTValidatorSet getValidatorSet() {
		return validatorSet;
	}

	public VerifiedVertex getGenesisVertex() {
		return genesisVertex;
	}

	public VerifiedLedgerHeaderAndProof getGenesisHeader() {
		return this.genesisHeader;
	}

	public QuorumCertificate getGenesisQC() {
		return genesisQC;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			this.validatorSet,
			this.genesisVertex,
			this.genesisQC,
			this.genesisHeader
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BFTConfiguration) {
			BFTConfiguration that = (BFTConfiguration) obj;
			return Objects.equals(this.validatorSet, that.validatorSet)
				&& Objects.equals(this.genesisVertex, that.genesisVertex)
				&& Objects.equals(this.genesisQC, that.genesisQC)
				&& Objects.equals(this.genesisHeader, that.genesisHeader);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[validatorSet=%s, genesisVertex=%s, genesisQC=%s, genesisHeader=%s]",
			getClass().getSimpleName(), this.validatorSet, this.genesisVertex, this.genesisQC, this.genesisHeader);
	}
}
