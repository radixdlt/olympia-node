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

package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("u_t")
public final class UnallocatedTokensParticle extends Particle {
	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("a")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	UnallocatedTokensParticle() {
		// Serializer only
		super();
	}

	public UnallocatedTokensParticle(
		UInt256 amount,
		RRI tokenDefinitionReference
	) {
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
	}

	public RadixAddress getAddress() {
		return this.tokenDefinitionReference.getAddress();
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]",
			getClass().getSimpleName(),
			tokenDefinitionReference,
			amount
		);
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UnallocatedTokensParticle)) {
			return false;
		}
		UnallocatedTokensParticle that = (UnallocatedTokensParticle) o;
		return Objects.equals(tokenDefinitionReference, that.tokenDefinitionReference)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tokenDefinitionReference, amount);
	}
}
