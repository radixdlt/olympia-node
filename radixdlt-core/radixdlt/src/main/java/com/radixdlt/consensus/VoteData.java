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
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/**
 * Serves as data which is voted upon in a round of BFT. In a standard implementation this would
 * only include a proposed vertex which extends the chain. In this implementation we include data
 * for both the parent and any vertex which would be committed if this data reaches a quorum of votes.
 * This way, vote processing can be handled statelessly.
 */
@Immutable
@SerializerId2("consensus.vote_data")
public final class VoteData {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("proposed")
	@DsonOutput(Output.ALL)
	private final BFTHeader proposed;

	@JsonProperty("parent")
	@DsonOutput(Output.ALL)
	private final BFTHeader parent;

	@JsonProperty("committed")
	@DsonOutput(Output.ALL)
	private final BFTHeader committed;

	@JsonCreator
	public VoteData(
		@JsonProperty("proposed") BFTHeader proposed,
		@JsonProperty("parent") BFTHeader parent,
		@JsonProperty("committed") BFTHeader committed
	) {
		this.proposed = Objects.requireNonNull(proposed);
		this.parent = Objects.requireNonNull(parent);
		this.committed = committed;
	}

	public BFTHeader getProposed() {
		return proposed;
	}

	public BFTHeader getParent() {
		return parent;
	}

	public Optional<BFTHeader> getCommitted() {
		return Optional.ofNullable(committed);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		VoteData that = (VoteData) o;
		return Objects.equals(proposed, that.proposed)
			&& Objects.equals(parent, that.parent)
			&& Objects.equals(committed, that.committed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(proposed, parent, committed);
	}
}
