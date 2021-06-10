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
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt384;

import java.util.Arrays;
import java.util.Objects;

@SerializerId2("radix.api.balance")
public class BalanceEntry {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("owner")
	@DsonOutput(DsonOutput.Output.ALL)
	private final REAddr owner;

	@JsonProperty("delegate")
	@DsonOutput(DsonOutput.Output.ALL)
	private final byte[] delegate;

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String rri;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt384 amount;

	@JsonProperty("negative")
	@DsonOutput(DsonOutput.Output.ALL)
	private final boolean negative;

	@JsonProperty("epochUnlocked")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Long epochUnlocked;

	@JsonCreator
	private static BalanceEntry create(
		@JsonProperty("owner") REAddr owner,
		@JsonProperty("delegate") byte[] delegate,
		@JsonProperty("rri") String rri,
		@JsonProperty("amount") UInt384 amount,
		@JsonProperty("negative") boolean negative,
		@JsonProperty("epochUnlocked") Long epochUnlocked
	) {
		return createFull(owner, delegate, rri, amount, negative, epochUnlocked);
	}


	public static BalanceEntry resource(
		String rri,
		UInt384 amount,
		boolean negative
	) {
		return createFull(null, null, rri, amount, negative, null);
	}

	public static BalanceEntry create(
		REAddr owner,
		ECPublicKey delegate,
		String rri,
		UInt384 amount,
		boolean negative,
		Long epochUnlocked
	) {
		return createFull(
			owner,
			delegate == null ? null : delegate.getCompressedBytes(),
			rri,
			amount,
			negative,
			epochUnlocked
		);
	}

	private BalanceEntry(
		REAddr owner, byte[] delegate, String rri,
		UInt384 amount, boolean negative, Long epochUnlocked
	) {
		this.owner = owner;
		this.delegate = delegate;
		this.rri = rri;
		this.amount = amount;
		this.negative = negative;
		this.epochUnlocked = epochUnlocked;
	}

	public static BalanceEntry createFull(
		REAddr owner,
		byte[] delegate,
		String rri,
		UInt384 amount,
		boolean negative,
		Long epochUnlocked
	) {
		Objects.requireNonNull(rri);
		Objects.requireNonNull(amount);

		return new BalanceEntry(owner, delegate, rri, amount, negative, epochUnlocked);
	}

	public static BalanceEntry createBalance(
		REAddr owner, ECPublicKey delegate, String rri, UInt384 amount
	) {
		return createFull(
			owner,
			delegate == null ? null : delegate.getCompressedBytes(),
			rri,
			amount,
			false,
			null
		);
	}

	public REAddr getOwner() {
		return owner;
	}

	public Long getEpochUnlocked() {
		return epochUnlocked;
	}

	public ECPublicKey getDelegate() {
		try {
			return delegate == null ? null : ECPublicKey.fromBytes(delegate);
		} catch (PublicKeyException e) {
			throw new IllegalStateException();
		}
	}

	public String rri() {
		return rri;
	}

	public UInt384 getAmount() {
		return amount;
	}

	public boolean isSupply() {
		return owner == null && delegate == null;
	}

	public ClientApiStore.BalanceType getType() {
		if (delegate == null) {
			return ClientApiStore.BalanceType.SPENDABLE;
		} else if (epochUnlocked == null) {
			return ClientApiStore.BalanceType.STAKES;
		} else {
			return ClientApiStore.BalanceType.UNSTAKES;
		}
	}

	public boolean isStake() {
		return delegate != null && epochUnlocked == null;
	}

	public boolean isUnstake() {
		return delegate != null && epochUnlocked != null;
	}

	public boolean isNegative() {
		return negative;
	}

	public BalanceEntry negate() {
		return new BalanceEntry(owner, delegate, rri, amount, !negative, epochUnlocked);
	}

	public BalanceEntry add(BalanceEntry balanceEntry) {
		assert Objects.equals(this.owner, balanceEntry.owner);
		assert this.rri.equals(balanceEntry.rri);

		if (negative) {
			return balanceEntry.negative ? sum(balanceEntry, true) : diff(balanceEntry, true);
		} else {
			return balanceEntry.negative ? diff(balanceEntry, false) : sum(balanceEntry, false);
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
				&& Objects.equals(owner, entry.owner)
				&& Objects.equals(epochUnlocked, entry.epochUnlocked)
				&& Arrays.equals(delegate, entry.delegate)
				&& rri.equals(entry.rri)
				&& amount.equals(entry.amount);
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(owner, Arrays.hashCode(delegate), rri, amount, negative, epochUnlocked);
	}

	@Override
	public String toString() {
		return "/" + getOwner() + "/" + rri + " = " + (negative ? "-" : "+") + amount.toString()
			+ (delegate == null ? "" : ", delegate " + getDelegate())
			+ (epochUnlocked == null ? "" : (", epochUnlocked " + epochUnlocked));
	}

	private BalanceEntry diff(BalanceEntry balanceEntry, boolean negate) {
		var isBigger = this.amount.compareTo(balanceEntry.amount) >= 0;
		var amount = isBigger
					 ? this.amount.subtract(balanceEntry.amount)
					 : balanceEntry.amount.subtract(this.amount);

		return new BalanceEntry(owner, delegate, rri, amount, negate == isBigger, epochUnlocked);
	}

	private BalanceEntry sum(BalanceEntry balanceEntry, boolean negative) {
		return new BalanceEntry(owner, delegate, rri, amount.add(balanceEntry.amount), negative, epochUnlocked);
	}
}
