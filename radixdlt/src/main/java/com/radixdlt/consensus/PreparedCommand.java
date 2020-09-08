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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * Results from a prepared stage execution. All fields should be persisted on ledger.
 */
@Immutable
@SerializerId2("consensus.prepared_command")
public final class PreparedCommand {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("stateVersion")
	@DsonOutput(Output.ALL)
	private final long stateVersion;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp;

	@JsonProperty("isEndOfEpoch")
	@DsonOutput(Output.ALL)
	private final boolean isEndOfEpoch;

	// TODO: Replace isEndOfEpoch with nextValidatorSet
	@JsonCreator
	private PreparedCommand(
		@JsonProperty("stateVersion") long stateVersion,
		@JsonProperty("timestamp") long timestamp,
		@JsonProperty("isEndOfEpoch") boolean isEndOfEpoch
	) {
		this.stateVersion = stateVersion;
		this.isEndOfEpoch = isEndOfEpoch;
		this.timestamp = timestamp;
	}

	public static PreparedCommand create(
		long stateVersion,
		long timestamp,
		boolean isEndOfEpoch
	) {
		return new PreparedCommand(stateVersion, timestamp, isEndOfEpoch);
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
		return Objects.hash(this.stateVersion, this.timestamp, this.isEndOfEpoch);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof PreparedCommand) {
			PreparedCommand other = (PreparedCommand) o;
			return this.timestamp == other.timestamp
				&& this.stateVersion == other.stateVersion
				&& this.isEndOfEpoch == other.isEndOfEpoch;
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s{stateVersion=%s timestamp=%s isEndOfEpoch=%s}",
			getClass().getSimpleName(), this.stateVersion, this.timestamp, this.isEndOfEpoch
		);
	}
}
