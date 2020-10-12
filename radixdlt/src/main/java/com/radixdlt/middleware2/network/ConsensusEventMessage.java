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

package com.radixdlt.middleware2.network;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.consensus.ConsensusEvent;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.ViewTimeout;
import com.radixdlt.consensus.Vote;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import org.radix.network.messaging.Message;

/**
 * The Data Transfer Object for Consensus messages. Each type of consensus message currently needs to be
 * a parameter in this class due to lack of interface serialization.
 */
@SerializerId2("message.consensus.event")
public class ConsensusEventMessage extends Message {
	@JsonProperty("view_timeout")
	@DsonOutput(Output.ALL)
	private final ViewTimeout viewTimeout;

	@JsonProperty("proposal")
	@DsonOutput(Output.ALL)
	private final Proposal proposal;

	@JsonProperty("vote")
	@DsonOutput(Output.ALL)
	private final Vote vote;

	ConsensusEventMessage() {
		// Serializer only
		super(0);
		this.viewTimeout = null;
		this.proposal = null;
		this.vote = null;
	}

	ConsensusEventMessage(int magic, ViewTimeout viewTimeout) {
		super(magic);
		this.viewTimeout = viewTimeout;
		this.proposal = null;
		this.vote = null;
	}

	ConsensusEventMessage(int magic, Proposal proposal) {
		super(magic);
		this.viewTimeout = null;
		this.proposal = proposal;
		this.vote = null;
	}

	ConsensusEventMessage(int magic, Vote vote) {
		super(magic);
		this.viewTimeout = null;
		this.proposal = null;
		this.vote = vote;
	}

	public ConsensusEvent getConsensusMessage() {
		ConsensusEvent event = consensusMessageInternal();
		if (event == null) {
			throw new IllegalStateException("No consensus message.");
		}
		return event;
	}

	private ConsensusEvent consensusMessageInternal() {
		if (this.viewTimeout != null) {
			return this.viewTimeout;
		}

		if (this.proposal != null) {
			return this.proposal;
		}

		if (this.vote != null) {
			return this.vote;
		}

		return null;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), consensusMessageInternal());
	}
}
