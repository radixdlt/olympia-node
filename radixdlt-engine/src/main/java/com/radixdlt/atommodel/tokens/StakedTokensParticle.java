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
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 *  A particle which represents an amount of staked fungible tokens
 *  owned by some key owner, stored in an account and staked to a delegate address.
 */
@SerializerId2("s_t")
public final class StakedTokensParticle extends Particle {
	@JsonProperty("d")
	@DsonOutput(Output.ALL)
	private RadixAddress delegateAddress;

	@JsonProperty("o")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("a")
	@DsonOutput(Output.ALL)
	private UInt256 amount;

	public StakedTokensParticle() {
		super();
	}

	public StakedTokensParticle(
		RadixAddress delegateAddress,
		RadixAddress address,
		UInt256 amount
	) {
		this.delegateAddress = Objects.requireNonNull(delegateAddress);
		this.address = Objects.requireNonNull(address);
		this.amount = Objects.requireNonNull(amount);
	}

	public RadixAddress getDelegateAddress() {
		return delegateAddress;
	}

	public RadixAddress getAddress() {
		return this.address;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s]",
			getClass().getSimpleName(),
			amount,
			address,
			delegateAddress
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
		if (!(o instanceof StakedTokensParticle)) {
			return false;
		}
		StakedTokensParticle that = (StakedTokensParticle) o;
		return Objects.equals(delegateAddress, that.delegateAddress)
			&& Objects.equals(address, that.address)
			&& Objects.equals(amount, that.amount);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			delegateAddress,
			address,
			amount
		);
	}
}
