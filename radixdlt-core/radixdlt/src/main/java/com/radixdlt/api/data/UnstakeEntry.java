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

package com.radixdlt.api.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import java.util.Arrays;
import java.util.Objects;

@SerializerId2("radix.api.unstake")
public class UnstakeEntry {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 amount;

	@JsonProperty("validator")
	@DsonOutput(DsonOutput.Output.ALL)
	private final byte[] validator;

	@JsonProperty("epochsUntil")
	@DsonOutput(DsonOutput.Output.ALL)
	private final int epochsUntil;

	@JsonProperty("withdrawTxID")
	@DsonOutput(DsonOutput.Output.ALL)
	private final AID withdrawTxID;

	private UnstakeEntry(
		UInt256 amount,
		byte[] validator,
		int epochsUntil,
		AID withdrawTxID
	) {
		this.validator = validator;
		this.epochsUntil = epochsUntil;
		this.withdrawTxID = withdrawTxID;
		this.amount = amount;
	}

	@JsonCreator
	private static UnstakeEntry create(
		@JsonProperty("amount") UInt256 amount,
		@JsonProperty("validator") byte[] validator,
		@JsonProperty("epochsUntil") int epochsUntil,
		@JsonProperty("withdrawTxID") AID withdrawTxID
	) {
		Objects.requireNonNull(amount);
		Objects.requireNonNull(validator);
		Objects.requireNonNull(withdrawTxID);

		return new UnstakeEntry(amount, validator, epochsUntil, withdrawTxID);
	}

	public static UnstakeEntry create(
		UInt256 amount,
		ECPublicKey validator,
		int epochsUntil,
		AID withdrawTxID
	) {
		return create(amount, validator.getCompressedBytes(), epochsUntil, withdrawTxID);
	}

	public UInt256 getAmount() {
		return amount;
	}

	public ECPublicKey getValidator() {
		try {
			return ECPublicKey.fromBytes(validator);
		} catch (PublicKeyException e) {
			throw new IllegalStateException();
		}
	}

	public int getEpochsUntil() {
		return epochsUntil;
	}

	public AID getWithdrawTxId() {
		return withdrawTxID;
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof UnstakeEntry) {
			var entry = (UnstakeEntry) o;

			return amount.equals(entry.amount)
				&& Arrays.equals(validator, entry.validator)
				&& epochsUntil == entry.epochsUntil
				&& withdrawTxID.equals(entry.withdrawTxID);
		}
		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(amount, validator, epochsUntil, withdrawTxID);
	}

	@Override
	public String toString() {
		return String.format("UnstakeEntry(%s, %s, %s, %s)", amount, validator, epochsUntil, withdrawTxID);
	}
}