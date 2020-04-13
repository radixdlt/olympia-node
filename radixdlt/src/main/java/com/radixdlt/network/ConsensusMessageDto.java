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

package com.radixdlt.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.ConsensusMessage;
import com.radixdlt.consensus.NewView;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.Vote;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

/**
 * The Data Transfer Object for Consensus messages. Each type of consensus message currently needs to be
 * a parameter in this class. Though "ugly" it is required due to:
 * 1. the current requirement of consensus to process messages in order
 * 2. the current interface design of MessageCentral which requires listeners to
 * register with a single class, otherwise in-order messages isn't guaranteed
 */
@SerializerId2("message.consensus.msg")
public class ConsensusMessageDto extends Message {
	@JsonProperty("newview")
	@DsonOutput(Output.ALL)
	private final NewView newView;

	@JsonProperty("proposal")
	@DsonOutput(Output.ALL)
	private final Proposal proposal;

	@JsonProperty("vote")
	@DsonOutput(Output.ALL)
	private final Vote vote;

	private ConsensusMessageDto() {
		// Serializer only
		super(0);
		this.newView = null;
		this.proposal = null;
		this.vote = null;
	}

	ConsensusMessageDto(int magic, NewView newView) {
		super(magic);
		this.newView = newView;
		this.proposal = null;
		this.vote = null;
	}

	ConsensusMessageDto(int magic, Proposal proposal) {
		super(magic);
		this.newView = null;
		this.proposal = proposal;
		this.vote = null;
	}

	ConsensusMessageDto(int magic, Vote vote) {
		super(magic);
		this.newView = null;
		this.proposal = null;
		this.vote = vote;
	}

	public ConsensusMessage getConsensusMessage() {
		return this.newView != null ? this.newView : this.proposal != null
			? this.proposal : this.vote;
	}
}
