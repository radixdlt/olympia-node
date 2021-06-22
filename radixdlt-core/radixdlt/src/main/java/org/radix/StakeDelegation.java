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

package org.radix;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 * Represents a delegation of stake from a staker to a delegate for
 * a specified amount.
 */
public final class StakeDelegation {
	private final ECPublicKey staker;
	private final ECPublicKey delegate;
	private final UInt256 amount;

	private StakeDelegation(ECPublicKey staker, ECPublicKey delegate, UInt256 amount) {
		this.staker = Objects.requireNonNull(staker);
		this.delegate = Objects.requireNonNull(delegate);
		this.amount = Objects.requireNonNull(amount);
	}

	public static StakeDelegation of(ECPublicKey staker, ECPublicKey delegate, UInt256 amount) {
		return new StakeDelegation(staker, delegate, amount);
	}

	public ECPublicKey staker() {
		return this.staker;
	}

	public ECPublicKey delegate() {
		return this.delegate;
	}

	public UInt256 amount() {
		return this.amount;
	}

	@Override
	public String toString() {
		return String.format("%s[%s->%s:%s]", getClass().getSimpleName(), this.staker, this.delegate, this.amount);
	}
}