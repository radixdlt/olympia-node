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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.Set;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("t")
public final class TransferrableTokensParticle extends Particle {
	@JsonProperty("o")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("r")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("g")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("a")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	@JsonProperty("m")
	@DsonOutput(Output.ALL)
	private boolean isMutable;

	private TransferrableTokensParticle() {
		super();
	}

	public TransferrableTokensParticle(
		RadixAddress address,
		UInt256 amount,
		UInt256 granularity,
		RRI tokenDefinitionReference,
		boolean isMutable
	) {
		this.address = Objects.requireNonNull(address);
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.amount = Objects.requireNonNull(amount);
		this.isMutable = isMutable;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.address.euid());
	}

	public boolean isMutable() {
		return isMutable;
	}

	public RadixAddress getAddress() {
		return this.address;
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			tokenDefinitionReference,
			amount,
			granularity,
			address,
			isMutable
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
		if (!(o instanceof TransferrableTokensParticle)) {
			return false;
		}
		TransferrableTokensParticle that = (TransferrableTokensParticle) o;
		return Objects.equals(address, that.address)
			&& Objects.equals(tokenDefinitionReference, that.tokenDefinitionReference)
			&& Objects.equals(granularity, that.granularity)
			&& Objects.equals(amount, that.amount)
			&& isMutable == that.isMutable;
	}

	@Override
	public int hashCode() {
		return Objects.hash(address, tokenDefinitionReference, granularity, amount, isMutable);
	}
}
