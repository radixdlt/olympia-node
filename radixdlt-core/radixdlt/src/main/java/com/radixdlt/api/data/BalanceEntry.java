/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.api.archive.to_deprecate.ClientApiStore;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
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

	@JsonProperty("txId")
	@DsonOutput(DsonOutput.Output.ALL)
	private final AID txId;

	@JsonCreator
	private static BalanceEntry create(
		@JsonProperty("owner") REAddr owner,
		@JsonProperty("delegate") byte[] delegate,
		@JsonProperty("rri") String rri,
		@JsonProperty("amount") UInt384 amount,
		@JsonProperty("negative") boolean negative,
		@JsonProperty("epochUnlocked") Long epochUnlocked,
		@JsonProperty("txId") AID txId
	) {
		return createFull(owner, delegate, rri, amount, negative, epochUnlocked, txId);
	}

	public static BalanceEntry resource(
		String rri,
		UInt384 amount,
		boolean negative
	) {
		return createFull(null, null, rri, amount, negative, null, null);
	}

	public static BalanceEntry create(
		REAddr owner,
		ECPublicKey delegate,
		String rri,
		UInt384 amount,
		boolean negative,
		Long epochUnlocked,
		AID txId
	) {
		return createFull(
			owner,
			delegate == null ? null : delegate.getCompressedBytes(),
			rri,
			amount,
			negative,
			epochUnlocked,
			txId
		);
	}

	private BalanceEntry(
		REAddr owner, byte[] delegate, String rri,
		UInt384 amount, boolean negative, Long epochUnlocked,
		AID txId
	) {
		this.owner = owner;
		this.delegate = delegate;
		this.rri = rri;
		this.amount = amount;
		this.negative = negative;
		this.epochUnlocked = epochUnlocked;
		this.txId = txId;
	}

	public static BalanceEntry createFull(
		REAddr owner,
		byte[] delegate,
		String rri,
		UInt384 amount,
		boolean negative,
		Long epochUnlocked,
		AID txId
	) {
		Objects.requireNonNull(rri);
		Objects.requireNonNull(amount);

		return new BalanceEntry(owner, delegate, rri, amount, negative, epochUnlocked, txId);
	}

	public static BalanceEntry createBalance(REAddr owner, ECPublicKey delegate, String rri, UInt384 amount) {
		return createFull(
			owner,
			delegate == null ? null : delegate.getCompressedBytes(),
			rri,
			amount,
			false,
			null,
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

	public AID getTxId() {
		return txId;
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
		return new BalanceEntry(owner, delegate, rri, amount, !negative, epochUnlocked, txId);
	}

	public BalanceEntry add(BalanceEntry balanceEntry) {
		assert Objects.equals(owner, balanceEntry.owner);
		assert rri.equals(balanceEntry.rri);

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
				&& amount.equals(entry.amount)
				&& Objects.equals(txId, entry.txId);
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(owner, Arrays.hashCode(delegate), rri, amount, negative, epochUnlocked, txId);
	}

	@Override
	public String toString() {
		return "/" + getOwner() + "/" + rri + " = " + (negative ? "-" : "+") + amount.toString()
			+ (delegate == null ? "" : ", delegate " + getDelegate())
			+ (epochUnlocked == null ? "" : (", epochUnlocked " + epochUnlocked));
	}

	private BalanceEntry diff(BalanceEntry balanceEntry, boolean negate) {
		var isBigger = amount.compareTo(balanceEntry.amount) >= 0;
		var amount = isBigger
					 ? this.amount.subtract(balanceEntry.amount)
					 : balanceEntry.amount.subtract(this.amount);

		return new BalanceEntry(owner, delegate, rri, amount, negate == isBigger, epochUnlocked, txId);
	}

	private BalanceEntry sum(BalanceEntry balanceEntry, boolean negative) {
		return new BalanceEntry(owner, delegate, rri, amount.add(balanceEntry.amount), negative, epochUnlocked, txId);
	}
}
