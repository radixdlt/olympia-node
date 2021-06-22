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

package com.radixdlt.ledger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.HashCode;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

@Immutable
@SerializerId2("ledger.accumulator_state")
public final class AccumulatorState {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("state_version")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	@JsonProperty("accumulator_hash")
	@DsonOutput(Output.ALL)
	private final HashCode accumulatorHash;

	@JsonCreator
	public AccumulatorState(
		@JsonProperty("state_version") long stateVersion,
		@JsonProperty("accumulator_hash") HashCode accumulatorHash
	) {
		this.stateVersion = stateVersion;
		this.accumulatorHash = Objects.requireNonNull(accumulatorHash);
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public HashCode getAccumulatorHash() {
		return accumulatorHash;
	}

	@Override
	public int hashCode() {
		return Objects.hash(stateVersion, accumulatorHash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AccumulatorState)) {
			return false;
		}

		AccumulatorState other = (AccumulatorState) o;
		return stateVersion == other.stateVersion
			&& Objects.equals(accumulatorHash, other.accumulatorHash);
	}

	@Override
	public String toString() {
		return String.format("%s{version=%s hash=%s}", getClass().getSimpleName(), stateVersion, accumulatorHash);
	}
}
