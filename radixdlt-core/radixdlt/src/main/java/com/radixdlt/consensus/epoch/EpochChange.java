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

package com.radixdlt.consensus.epoch;

import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.LedgerProof;
import java.util.Objects;

/**
 * An epoch change message to consensus
 */
public final class EpochChange {
	private final LedgerProof proof;
	private final BFTConfiguration bftConfiguration;

	public EpochChange(LedgerProof proof, BFTConfiguration bftConfiguration) {
		this.proof = Objects.requireNonNull(proof);
		this.bftConfiguration = Objects.requireNonNull(bftConfiguration);
	}

	public BFTConfiguration getBFTConfiguration() {
		return bftConfiguration;
	}

	public LedgerProof getGenesisHeader() {
		return bftConfiguration.getVertexStoreState().getRootHeader();
	}

	public long getEpoch() {
		return proof.getEpoch() + 1;
	}

	public LedgerProof getProof() {
		return proof;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.proof, this.bftConfiguration);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof EpochChange) {
			final var that = (EpochChange) o;
			return Objects.equals(this.proof, that.proof)
				&& Objects.equals(this.bftConfiguration, that.bftConfiguration);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format(
			"%s{proof=%s config=%s}", this.getClass().getSimpleName(), proof, bftConfiguration
		);
	}
}
