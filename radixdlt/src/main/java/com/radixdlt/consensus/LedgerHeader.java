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
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
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

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	@JsonProperty("epoch")
	@DsonOutput(Output.ALL)
	private final long epoch;

	// TODO: remove this
	private final View view;

	@JsonProperty("accumulator")
	@DsonOutput(Output.ALL)
	private final Hash accumulator;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp; // TODO: Move into command accumulator

	@JsonProperty("isEndOfEpoch")
	@DsonOutput(Output.ALL)
	private final boolean isEndOfEpoch;

	// TODO: Replace isEndOfEpoch with nextValidatorSet
	@JsonCreator
	private LedgerHeader(
		@JsonProperty("epoch") long epoch,
		@JsonProperty("view") long view,
		@JsonProperty("stateVersion") long stateVersion,
		@JsonProperty("accumulator") Hash accumulator,
		@JsonProperty("timestamp") long timestamp,
		@JsonProperty("isEndOfEpoch") boolean isEndOfEpoch
	) {
		this(epoch, View.of(view), stateVersion, accumulator, timestamp, isEndOfEpoch);
	}

	private LedgerHeader(
		long epoch,
		View view,
		long stateVersion,
		Hash accumulator,
		long timestamp,
		boolean isEndOfEpoch
	) {
		this.epoch = epoch;
		this.view = view;
		this.stateVersion = stateVersion;
		this.accumulator = accumulator;
		this.isEndOfEpoch = isEndOfEpoch;
		this.timestamp = timestamp;
	}

	public static LedgerHeader genesis(Hash accumulator) {
		return new LedgerHeader(
			0, View.genesis(), 0, accumulator, 0, true
		);
	}

	public static LedgerHeader create(
		long epoch,
		View view,
		long stateVersion,
		Hash commandId,
		long timestamp,
		boolean isEndOfEpoch
	) {
		return new LedgerHeader(epoch, view, stateVersion, commandId, timestamp, isEndOfEpoch);
	}

	@JsonProperty("view")
	@DsonOutput(Output.ALL)
	private Long getSerializerView() {
		return this.view == null ? null : this.view.number();
	}

	public View getView() {
		return view;
	}

	public Hash getAccumulator() {
		return accumulator;
	}

	public long getEpoch() {
		return epoch;
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public boolean isEndOfEpoch() {
		return isEndOfEpoch;
	}

	public long timestamp() {
		return this.timestamp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.stateVersion, this.accumulator, this.timestamp, this.epoch, this.view, this.isEndOfEpoch);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof LedgerHeader) {
			LedgerHeader other = (LedgerHeader) o;
			return this.timestamp == other.timestamp
				&& this.stateVersion == other.stateVersion
				&& Objects.equals(this.accumulator, other.accumulator)
				&& this.epoch == other.epoch
				&& Objects.equals(this.view, other.view)
				&& this.isEndOfEpoch == other.isEndOfEpoch;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{stateVersion=%s timestamp=%s epoch=%s, isEndOfEpoch=%s}",
			getClass().getSimpleName(), this.stateVersion, this.timestamp, this.epoch, this.isEndOfEpoch
		);
	}
}
