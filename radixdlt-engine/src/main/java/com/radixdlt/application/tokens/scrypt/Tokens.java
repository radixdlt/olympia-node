/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.application.tokens.scrypt;

import com.radixdlt.constraintmachine.exceptions.InvalidResourceException;
import com.radixdlt.constraintmachine.exceptions.NotEnoughResourcesException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Objects;

public final class Tokens {
	private final REAddr resourceAddr;
	private final UInt384 amount;

	private Tokens(
		REAddr resourceAddr,
		UInt384 amount
	) {
		this.resourceAddr = resourceAddr;
		this.amount = amount;
	}

	public static Tokens create(REAddr resourceAddr, UInt256 amount) {
		return new Tokens(resourceAddr, UInt384.from(amount));
	}

	public static Tokens zero(REAddr resourceAddr) {
		return new Tokens(resourceAddr, UInt384.ZERO);
	}

	public UInt384 getAmount() {
		return amount;
	}

	public REAddr getResourceAddr() {
		return resourceAddr;
	}

	Pair<Tokens, Tokens> split(UInt256 first) throws NotEnoughResourcesException {
		var first384 = UInt384.from(first);
		if (amount.compareTo(first384) < 0) {
			throw new NotEnoughResourcesException(first, amount.getLow());
		}

		var second384 = this.amount.subtract(first384);
		return Pair.of(new Tokens(resourceAddr, first384), new Tokens(resourceAddr, second384));
	}

	Tokens merge(Tokens tokens) throws InvalidResourceException {
		if (!this.resourceAddr.equals(tokens.resourceAddr)) {
			throw new InvalidResourceException(this.resourceAddr, tokens.resourceAddr);
		}

		var amount = tokens.amount.add(this.amount);
		return new Tokens(resourceAddr, amount);
	}

	public boolean isZero() {
		return amount.isZero();
	}

	@Override
	public int hashCode() {
		return Objects.hash(resourceAddr, amount);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Tokens)) {
			return false;
		}

		var other = (Tokens) o;
		return Objects.equals(this.amount, other.amount)
			&& Objects.equals(this.resourceAddr, other.resourceAddr);
	}

	@Override
	public String toString() {
		return String.format("%s{resource=%s tokens=%s}", this.getClass().getSimpleName(), this.resourceAddr, this.amount);
	}
}
