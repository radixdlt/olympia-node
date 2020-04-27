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

package com.radixdlt.consensus.validators;

import com.radixdlt.utils.UInt256;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

/**
 * Represents a validator and their Proof-of-Stake status.
 * <p>
 * Serializable on the assumption that this will somehow need
 * to be persisted at some point.
 */
@Immutable
@SerializerId2("consensus.validator")
public final class Validator {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	private SerializerDummy serializer = SerializerDummy.DUMMY;

/*	FIXME: Functionality not required until drop 2
	public enum BondStatus {
		UNBONDED,
		BONDED,
		UNBONDING;
	}

	// Current bonding status
	private BondStatus bondStatus;

	@JsonProperty("reward_address")
	@DsonOutput(Output.ALL)
	// Address for rewards
	private final RadixAddress rewardAddress;

	@JsonProperty("bond_view")
	@DsonOutput(Output.ALL)
	// View when bonding occurred, possibly null
	private final View bondView;

	@JsonProperty("unbond_view")
	@DsonOutput(Output.ALL)
	// View when unbonding started, possibly null
	private final View unbondView;

	@JsonProperty("unbond_completion")
	@DsonOutput(Output.ALL)
	// Unix millisecond time when unbonding finishes, only valid if unboundView != null
	private final long unbondCompletion;

	@JsonProperty("jailed")
	@DsonOutput(Output.ALL)
	// True if jailed, false otherwise
	private final boolean jailed;
*/

	@JsonProperty("power")
	@DsonOutput(Output.ALL)
	// Staked tokens
	private final UInt256 power;

    // Public key for consensus
	private ECPublicKey nodeKey;

	Validator() {
		// Serializer only
		this.power = null;
	}


	private Validator(
		ECPublicKey nodeKey,
		UInt256 power
	) {
		this.nodeKey = Objects.requireNonNull(nodeKey);
		this.power = Objects.requireNonNull(power);
	}

	public static Validator from(ECPublicKey nodeKey, UInt256 power) {
		return new Validator(nodeKey, power);
	}

	public ECPublicKey nodeKey() {
		return this.nodeKey;
	}

	public UInt256 getPower() {
		return power;
	}

	// Property "node_key" - 1 getter, 1 setter
	@JsonProperty("node_key")
	@DsonOutput(Output.ALL)
	byte[] getJsonNodeKey() {
		return (this.nodeKey == null) ? null : nodeKey.getBytes();
	}

	@JsonProperty("node_key")
	void setJsonKey(byte[] newKey) throws SerializationException {
		try {
			this.nodeKey = new ECPublicKey(newKey);
		} catch (CryptoException cex) {
			throw new SerializationException("Invalid key", cex);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.nodeKey, this.power);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof Validator) {
			Validator other = (Validator) obj;
			return Objects.equals(this.nodeKey, other.nodeKey)
				&& Objects.equals(this.power, other.power);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{nodeKey=%s power=%s}", getClass().getSimpleName(), this.nodeKey, this.power);
	}
}
