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

package com.radixdlt.consensus;

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

/**
 * {@link VoteData} with a timestamp.
 */
@Immutable
@SerializerId2("consensus.timestamped_vote_data")
public final class TimestampedVoteData {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("opaque")
	@DsonOutput(Output.ALL)
	private final HashCode opaque;

	@JsonProperty("header")
	@DsonOutput(Output.ALL)
	private final LedgerHeader header;

	@JsonProperty("ts")
	@DsonOutput(Output.ALL)
	private final long nodeTimestamp;

	@JsonCreator
	public TimestampedVoteData(
		@JsonProperty("opaque") HashCode opaque,
		@JsonProperty("header") LedgerHeader header,
		@JsonProperty("ts") long nodeTimestamp
	) {
		this.opaque = opaque;
		this.nodeTimestamp = nodeTimestamp;
		this.header = header;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof TimestampedVoteData) {
			TimestampedVoteData that = (TimestampedVoteData) o;
			return this.nodeTimestamp == that.nodeTimestamp
				&& Objects.equals(this.opaque, that.opaque)
				&& Objects.equals(this.header, that.header);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.nodeTimestamp, this.header, this.opaque);
	}
}