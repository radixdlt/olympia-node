/*
 *  (C) Copyright 2020 Radix DLT Ltd
 *
 *  Radix DLT Ltd licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License.  You may obtain a copy of the
 *  License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.  See the License for the specific
 *  language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.syncer.PreparedCommand;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.UInt256;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@Immutable
@SerializerId2("consensus.vertex_metadata")
public final class VertexMetadata {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private View view;

	@JsonProperty("id")
	@DsonOutput(Output.ALL)
	private final Hash id;

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	private BFTValidatorSet validatorSet;

	@JsonProperty("timestamped_signatures_hash")
	@DsonOutput(Output.ALL)
	private final Hash timestampedSignaturesHash;

	VertexMetadata() {
		// Serializer only
		this.view = null;
		this.id = null;
		this.stateVersion = 0L;
		this.epoch = 0L;
		this.validatorSet = null;
		this.timestampedSignaturesHash = null;
	}

	// TODO: Move executor data to a more opaque data structure
	public VertexMetadata(
		long epoch, // consensus data
		View view, // consensus data
		Hash id, // consensus data
		long stateVersion, // executor data
		BFTValidatorSet validatorSet, // executor data
		Hash timestampedSignaturesHash // executor data
	) {
		if (epoch < 0) {
			throw new IllegalArgumentException("epoch must be >= 0");
		}

		if (stateVersion < 0) {
			throw new IllegalArgumentException("stateVersion must be >= 0");
		}

		this.epoch = epoch;
		this.stateVersion = stateVersion;
		this.view = view;
		this.id = id;
		this.validatorSet = validatorSet;
		this.timestampedSignaturesHash = timestampedSignaturesHash;
	}

	public static VertexMetadata ofGenesisAncestor(BFTValidatorSet initialValidatorSet) {
		return new VertexMetadata(
			0,
			View.genesis(),
			Hash.ZERO_HASH,
			0,
			initialValidatorSet,
			Hash.ZERO_HASH
		);
	}

	public static VertexMetadata ofGenesisVertex(Vertex vertex) {
		return new VertexMetadata(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(),
			vertex.getQC().getParent().getStateVersion(),
			null,
			Hash.ZERO_HASH
		);
	}

	public static VertexMetadata ofVertex(Vertex vertex, PreparedCommand preparedCommand) {
		return new VertexMetadata(
			vertex.getEpoch(),
			vertex.getView(),
			vertex.getId(),
			preparedCommand.getStateVersion(),
			preparedCommand.getNextValidatorSet().orElse(null),
			preparedCommand.getTimestampedSignaturesHash()
		);
	}

	public Optional<BFTValidatorSet> getValidatorSet() {
		return Optional.ofNullable(validatorSet);
	}

	public boolean isEndOfEpoch() {
		return this.validatorSet != null;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public View getView() {
		return view;
	}

	public Hash getId() {
		return id;
	}

	@JsonProperty("validator_set")
	@DsonOutput(Output.ALL)
	private Map<String, UInt256> getValidatorSetJson() {
		if (validatorSet == null) {
			return null;
		}
		return validatorSet.getValidators().stream()
			.collect(ImmutableMap.toImmutableMap(v -> encodePublicKey(v.getNode()), BFTValidator::getPower));
	}

	// TODO: Use base64 over hex
	private static String encodePublicKey(BFTNode key) {
		return Bytes.toHexString(key.getKey().getBytes());
	}

	private static BFTNode toBFTNode(String str) {
		try {
			return BFTNode.create(new ECPublicKey(Bytes.fromHexString(str)));
		} catch (CryptoException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}

	@JsonProperty("validator_set")
	private void setValidatorSetJson(Map<String, UInt256> vset) {
		List<BFTValidator> validators = vset.entrySet().stream()
			.map(e -> BFTValidator.from(toBFTNode(e.getKey()), e.getValue()))
			.collect(Collectors.toList());
		this.validatorSet = BFTValidatorSet.from(validators);
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("view")
	private void setSerializerView(Long number) {
		this.view = number == null ? null : View.of(number);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.view, this.id, this.stateVersion, this.validatorSet, this.epoch, this.timestampedSignaturesHash);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof VertexMetadata) {
			VertexMetadata other = (VertexMetadata) o;
			return
				Objects.equals(this.view, other.view)
				&& Objects.equals(this.id, other.id)
				&& Objects.equals(this.timestampedSignaturesHash, other.timestampedSignaturesHash)
				&& Objects.equals(this.validatorSet, other.validatorSet)
				&& this.stateVersion == other.stateVersion
				&& this.epoch == other.epoch;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{epoch=%s view=%s stateVersion=%s validatorSet=%s}",
			getClass().getSimpleName(), this.epoch, this.view, this.stateVersion, this.validatorSet
		);
	}
}