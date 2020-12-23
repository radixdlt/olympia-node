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
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import java.util.Objects;

/**
 * Configuration for a bft instance which should be shared by all validators in the bft.
 */
public final class BFTConfiguration {
	private final BFTValidatorSet validatorSet;
	private final VerifiedVertexStoreState vertexStoreState;

	public BFTConfiguration(
		BFTValidatorSet validatorSet,
		VerifiedVertexStoreState vertexStoreState
	) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.vertexStoreState = Objects.requireNonNull(vertexStoreState);
	}

	public BFTValidatorSet getValidatorSet() {
		return validatorSet;
	}

	public VerifiedVertexStoreState getVertexStoreState() {
		return vertexStoreState;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			this.validatorSet,
			this.vertexStoreState
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof BFTConfiguration) {
			BFTConfiguration that = (BFTConfiguration) obj;
			return Objects.equals(this.validatorSet, that.validatorSet)
				&& Objects.equals(this.vertexStoreState, that.vertexStoreState);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[validatorSet=%s, vertexStoreState=%s]",
			getClass().getSimpleName(), this.validatorSet, this.vertexStoreState);
	}
}
