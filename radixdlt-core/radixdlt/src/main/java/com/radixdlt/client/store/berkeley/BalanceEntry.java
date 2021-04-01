/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.store.berkeley;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

@SerializerId2("radix.api.balance")
public class BalanceEntry {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("owner")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress owner;

	@JsonProperty("delegate")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RadixAddress delegate;

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final RRI rri;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 granularity;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 amount;

	@JsonProperty("negative")
	@DsonOutput(DsonOutput.Output.ALL)
	private final boolean negative;

	private BalanceEntry(RadixAddress owner, RadixAddress delegate, RRI rri, UInt256 granularity, UInt256 amount, boolean negative) {
		this.owner = owner;
		this.delegate = delegate;
		this.rri = rri;
		this.granularity = granularity;
		this.amount = amount;
		this.negative = negative;
	}

	@JsonCreator
	public static BalanceEntry createFull(
		@JsonProperty("owner") RadixAddress owner,
		@JsonProperty("delegate") RadixAddress delegate,
		@JsonProperty("rri") RRI rri,
		@JsonProperty("granularity") UInt256 granularity,
		@JsonProperty("amount") UInt256 amount,
		@JsonProperty("negative") boolean negative
	) {
		Objects.requireNonNull(owner);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(granularity);
		Objects.requireNonNull(amount);

		return new BalanceEntry(owner, delegate, rri, granularity, amount, false);
	}

	public static BalanceEntry create(RadixAddress owner, RadixAddress delegate, RRI rri, UInt256 granularity, UInt256 amount) {
		return createFull(owner, delegate, rri, granularity, amount, false);
	}

	public String toKey() {
		return owner.toString() + "/" + rri.getName() + (delegate == null ? "" : "/" + delegate.toString());
	}

	public RadixAddress getOwner() {
		return owner;
	}

	public RadixAddress getDelegate() {
		return delegate;
	}

	public RRI getRri() {
		return rri;
	}

	public UInt256 getGranularity() {
		return granularity;
	}

	public UInt256 getAmount() {
		return amount;
	}

	public boolean isSupply() {
		return owner.equals(rri.getAddress());
	}

	public boolean isStake() {
		return delegate != null;
	}

	public boolean isNegative() {
		return negative;
	}

	public BalanceEntry negate() {
		return new BalanceEntry(owner, delegate, rri, granularity, amount, !negative);
	}

	public BalanceEntry subtract(BalanceEntry balanceEntry) {
		assert this.owner.equals(balanceEntry.owner);
		assert this.rri.equals(balanceEntry.rri);

		if (negative) {
			return balanceEntry.negative ? diff(balanceEntry, true) : sum(balanceEntry, true);
		} else {
			return balanceEntry.negative ? sum(balanceEntry, false) : diff(balanceEntry, false);
		}
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof BalanceEntry) {
			var entry = (BalanceEntry) o;

			return negative == entry.negative
				&& owner.equals(entry.owner)
				&& Objects.equals(delegate, entry.delegate)
				&& rri.equals(entry.rri)
				&& granularity.equals(entry.granularity)
				&& amount.equals(entry.amount);
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(owner, delegate, rri, granularity, amount, negative);
	}

	@Override
	public String toString() {
		return "/" + owner + "/" + rri.getName() + " = " + (negative ? "-" : "+") + amount.toString()
			+ (delegate == null ? "" : ", delegate " + delegate.toString());
	}

	private BalanceEntry diff(BalanceEntry balanceEntry, boolean negate) {
		var isBigger = this.amount.compareTo(balanceEntry.amount) >= 0;
		var amount = isBigger
					 ? this.amount.subtract(balanceEntry.amount)
					 : balanceEntry.amount.subtract(this.amount);

		return new BalanceEntry(owner, delegate, rri, granularity, amount, negate == isBigger);
	}

	private BalanceEntry sum(BalanceEntry balanceEntry, boolean negative) {
		return new BalanceEntry(owner, delegate, rri, granularity, amount.add(balanceEntry.amount), negative);
	}
}
