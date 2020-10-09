/*
 * (C) Copyright 2020 Radix DLT Ltd
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
 */

package com.radixdlt.consensus.bft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;


/**
 * Represents a validator and their Proof-of-Stake status.
 */
@Immutable
@SerializerId2("consensus.bft_validator")
public final class BFTValidator {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput({Output.ALL})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	// Power associated with each validator, could e.g. be based on staked tokens
	@JsonProperty("power")
	@DsonOutput({Output.ALL})
	private final UInt256 power;

    // Public key for consensus
	private final BFTNode node;

	@JsonCreator
	private BFTValidator(
		@JsonProperty("node") String nodeKey,
		@JsonProperty("power") UInt256 power
	) {
		this.node = Objects.requireNonNull(toBFTNode(nodeKey));
		this.power = Objects.requireNonNull(power);
	}

	private BFTValidator(
		BFTNode node,
		UInt256 power
	) {
		this.node = Objects.requireNonNull(node);
		this.power = Objects.requireNonNull(power);
	}

	public static BFTValidator from(BFTNode node, UInt256 power) {
		return new BFTValidator(node, power);
	}

	public BFTNode getNode() {
		return node;
	}

	public UInt256 getPower() {
		return power;
	}

	@JsonProperty("node")
	@DsonOutput(Output.ALL)
	private String getSerializerNodeKey() {
		return encodePublicKey(this.node);
	}

	private static String encodePublicKey(BFTNode key) {
		return Bytes.toHexString(key.getKey().getBytes());
	}

	private static BFTNode toBFTNode(String str) {
		try {
			return BFTNode.fromPublicKeyBytes(Bytes.fromHexString(str));
		} catch (PublicKeyException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.node, this.power);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof BFTValidator) {
			BFTValidator other = (BFTValidator) obj;
			return Objects.equals(this.node, other.node)
				&& Objects.equals(this.power, other.power);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{node=%s power=%s}", getClass().getSimpleName(), this.node.getSimpleName(), this.power);
	}
}
