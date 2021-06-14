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

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Ledger accumulator which gets voted and agreed upon
 */
@Immutable
@SerializerId2("consensus.ledger_header")
public final class LedgerHeader {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	private final View view;

	@JsonProperty("accumulator_state")
	@DsonOutput(Output.ALL)
	private final AccumulatorState accumulatorState;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp; // TODO: Move into command accumulator

	@JsonProperty("next_validators")
	@DsonOutput(Output.ALL)
	private final ImmutableSet<BFTValidator> nextValidators;

	private final Optional<HashCode> nextForkHash;

	// TODO: Replace isEndOfEpoch with nextValidatorSet
	@JsonCreator
	private LedgerHeader(
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") long view,
		@JsonProperty("accumulator_state") AccumulatorState accumulatorState,
		@JsonProperty("timestamp") long timestamp,
		@JsonProperty("next_validators") ImmutableSet<BFTValidator> nextValidators,
		@JsonProperty("next_fork_hash") HashCode nextForkHash
	) {
		this(epoch, View.of(view), accumulatorState, timestamp, nextValidators, Optional.ofNullable(nextForkHash));
	}

	private LedgerHeader(
		long epoch,
		View view,
		AccumulatorState accumulatorState,
		long timestamp,
		ImmutableSet<BFTValidator> nextValidators,
		Optional<HashCode> nextForkHash
	) {
		this.epoch = epoch;
		this.view = view;
		this.accumulatorState = accumulatorState;
		this.nextValidators = nextValidators;
		this.timestamp = timestamp;
		this.nextForkHash = Objects.requireNonNull(nextForkHash);
	}

	public JSONObject asJSONObject() {
		var json = new JSONObject()
			.put("epoch", epoch)
			.put("view", view.number())
			.put("version", accumulatorState.getStateVersion())
			.put("accumulator", Bytes.toHexString(accumulatorState.getAccumulatorHash().asBytes()))
			.put("timestamp", timestamp);

		if (nextValidators != null) {
			var validators = new JSONArray();
			for (var v : nextValidators) {
				var validatorAddress = ValidatorAddress.of(v.getNode().getKey());
				validators.put(new JSONObject()
					.put("address", validatorAddress)
					.put("stake", v.getPower())
				);
			}
			json.put("nextValidators", validators);
		}

		nextForkHash.ifPresent(nfh -> json.put("next_fork_hash", nfh));

		return json;
	}


	public static LedgerHeader mocked() {
		return new LedgerHeader(0, View.genesis(), new AccumulatorState(0,  HashUtils.zero256()), 0, null, Optional.empty());
	}

	public static LedgerHeader genesis(AccumulatorState accumulatorState, BFTValidatorSet nextValidators, long timestamp) {
		return new LedgerHeader(
			0, View.genesis(), accumulatorState, timestamp,
			nextValidators == null ? null : nextValidators.getValidators(),
			Optional.empty()
		);
	}

	public static LedgerHeader create(
		long epoch,
		View view,
		AccumulatorState accumulatorState,
		long timestamp
	) {
		return new LedgerHeader(epoch, view, accumulatorState, timestamp, null, Optional.empty());
	}

	public static LedgerHeader create(
		long epoch,
		View view,
		AccumulatorState accumulatorState,
		long timestamp,
		BFTValidatorSet validatorSet,
		Optional<HashCode> nextForkHash
	) {
		return new LedgerHeader(
			epoch,
			view,
			accumulatorState,
			timestamp,
			validatorSet == null ? null : validatorSet.getValidators(),
			nextForkHash
		);
	}

	public LedgerHeader updateViewAndTimestamp(View view, long timestamp) {
		return new LedgerHeader(
			this.epoch,
			view,
			this.accumulatorState,
			timestamp,
			this.nextValidators,
			this.nextForkHash
		);
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	@JsonProperty("next_fork_hash")
	@DsonOutput(Output.ALL)
	private HashCode getSerializerNextForkHash() {
		return this.nextForkHash.isPresent() ? this.nextForkHash.get() : null;
	}

	public View getView() {
		return view;
	}

	public Optional<BFTValidatorSet> getNextValidatorSet() {
		return Optional.ofNullable(nextValidators).map(BFTValidatorSet::from);
	}

	public Optional<HashCode> getNextForkHash() {
		return nextForkHash;
	}

	public AccumulatorState getAccumulatorState() {
		return accumulatorState;
	}

	public long getEpoch() {
		return epoch;
	}

	public boolean isEndOfEpoch() {
		return nextValidators != null;
	}

	public long timestamp() {
		return this.timestamp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.accumulatorState, this.timestamp, this.epoch, this.view, this.nextValidators, this.nextForkHash);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof LedgerHeader) {
			final var other = (LedgerHeader) o;
			return this.timestamp == other.timestamp
				&& Objects.equals(this.accumulatorState, other.accumulatorState)
				&& this.epoch == other.epoch
				&& Objects.equals(this.view, other.view)
				&& Objects.equals(this.nextValidators, other.nextValidators)
				&& Objects.equals(this.nextForkHash, other.nextForkHash);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{accumulator=%s timestamp=%s epoch=%s view=%s nextValidators=%s, nextForkHash=%s}",
			getClass().getSimpleName(), this.accumulatorState, this.timestamp, this.epoch, this.view, this.nextValidators, this.nextForkHash
		);
	}
}
