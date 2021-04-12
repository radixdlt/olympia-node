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
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("t")
public final class TokensParticle extends Particle {
	@JsonProperty("o")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("r")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("a")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	@JsonProperty("b")
	@DsonOutput(Output.ALL)
	private boolean isBurnable;

	private TokensParticle() {
		super();
	}

	public TokensParticle(
		RadixAddress address,
		UInt256 amount,
		RRI tokenDefinitionReference,
		boolean isBurnable
	) {
		this.address = Objects.requireNonNull(address);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
		this.isBurnable = isBurnable;
	}

	public boolean isBurnable() {
		return isBurnable;
	}

	public RadixAddress getAddress() {
		return this.address;
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s]",
			getClass().getSimpleName(),
			tokenDefinitionReference,
			amount,
			address,
			isBurnable
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
		if (!(o instanceof TokensParticle)) {
			return false;
		}
		TokensParticle that = (TokensParticle) o;
		return Objects.equals(address, that.address)
			&& Objects.equals(tokenDefinitionReference, that.tokenDefinitionReference)
			&& Objects.equals(amount, that.amount)
			&& isBurnable == that.isBurnable;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, tokenDefinitionReference, amount, isBurnable);
	}
}
