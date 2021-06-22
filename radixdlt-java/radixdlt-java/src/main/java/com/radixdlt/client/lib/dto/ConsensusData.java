/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ConsensusData {
	private final long stateVersion;
	private final long voteQuorums;
	private final long rejected;
	private final long vertexStoreRebuilds;
	private final long vertexStoreForks;
	private final long timeout;
	private final long vertexStoreSize;
	private final long processed;
	private final long consensusEvents;
	private final long indirectParent;
	private final long proposalsMade;
	private final long timedOutViews;
	private final long timeoutQuorums;
	private final ConsensusDataSync sync;

	private ConsensusData(
		long stateVersion,
		long voteQuorums,
		long rejected,
		long vertexStoreRebuilds,
		long vertexStoreForks,
		long timeout,
		long vertexStoreSize,
		long processed,
		long consensusEvents,
		long indirectParent,
		long proposalsMade,
		long timedOutViews,
		long timeoutQuorums,
		ConsensusDataSync sync
	) {
		this.stateVersion = stateVersion;
		this.voteQuorums = voteQuorums;
		this.rejected = rejected;
		this.vertexStoreRebuilds = vertexStoreRebuilds;
		this.vertexStoreForks = vertexStoreForks;
		this.timeout = timeout;
		this.vertexStoreSize = vertexStoreSize;
		this.processed = processed;
		this.consensusEvents = consensusEvents;
		this.indirectParent = indirectParent;
		this.proposalsMade = proposalsMade;
		this.timedOutViews = timedOutViews;
		this.timeoutQuorums = timeoutQuorums;
		this.sync = sync;
	}

	@JsonCreator
	public static ConsensusData create(
		@JsonProperty(value = "stateVersion", required = true) long stateVersion,
		@JsonProperty(value = "voteQuorums", required = true) long voteQuorums,
		@JsonProperty(value = "rejected", required = true) long rejected,
		@JsonProperty(value = "vertexStoreRebuilds", required = true) long vertexStoreRebuilds,
		@JsonProperty(value = "vertexStoreForks", required = true) long vertexStoreForks,
		@JsonProperty(value = "timeout", required = true) long timeout,
		@JsonProperty(value = "vertexStoreSize", required = true) long vertexStoreSize,
		@JsonProperty(value = "processed", required = true) long processed,
		@JsonProperty(value = "consensusEvents", required = true) long consensusEvents,
		@JsonProperty(value = "indirectParent", required = true) long indirectParent,
		@JsonProperty(value = "proposalsMade", required = true) long proposalsMade,
		@JsonProperty(value = "timedOutViews", required = true) long timedOutViews,
		@JsonProperty(value = "timeoutQuorums", required = true) long timeoutQuorums,
		@JsonProperty(value = "sync", required = true) ConsensusDataSync sync
	) {
		return new ConsensusData(
			stateVersion,
			voteQuorums,
			rejected,
			vertexStoreRebuilds,
			vertexStoreForks,
			timeout,
			vertexStoreSize,
			processed,
			consensusEvents,
			indirectParent,
			proposalsMade,
			timedOutViews,
			timeoutQuorums,
			sync
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof ConsensusData)) {
			return false;
		}

		var that = (ConsensusData) o;
		return stateVersion == that.stateVersion
			&& voteQuorums == that.voteQuorums
			&& rejected == that.rejected
			&& vertexStoreRebuilds == that.vertexStoreRebuilds
			&& vertexStoreForks == that.vertexStoreForks
			&& timeout == that.timeout
			&& vertexStoreSize == that.vertexStoreSize
			&& processed == that.processed
			&& consensusEvents == that.consensusEvents
			&& indirectParent == that.indirectParent
			&& proposalsMade == that.proposalsMade
			&& timedOutViews == that.timedOutViews
			&& timeoutQuorums == that.timeoutQuorums
			&& sync.equals(that.sync);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			stateVersion,
			voteQuorums,
			rejected,
			vertexStoreRebuilds,
			vertexStoreForks,
			timeout,
			vertexStoreSize,
			processed,
			consensusEvents,
			indirectParent,
			proposalsMade,
			timedOutViews,
			timeoutQuorums,
			sync
		);
	}

	@Override
	public String toString() {
		return "{stateVersion:" + stateVersion
			+ ", voteQuorums:" + voteQuorums
			+ ", rejected:" + rejected
			+ ", vertexStoreRebuilds:" + vertexStoreRebuilds
			+ ", vertexStoreForks:" + vertexStoreForks
			+ ", timeout:" + timeout
			+ ", vertexStoreSize:" + vertexStoreSize
			+ ", processed:" + processed
			+ ", consensusEvents:" + consensusEvents
			+ ", indirectParent:" + indirectParent
			+ ", proposalsMade:" + proposalsMade
			+ ", timedOutViews:" + timedOutViews
			+ ", timeoutQuorums:" + timeoutQuorums
			+ ", sync:" + sync + '}';
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public long getVoteQuorums() {
		return voteQuorums;
	}

	public long getRejected() {
		return rejected;
	}

	public long getVertexStoreRebuilds() {
		return vertexStoreRebuilds;
	}

	public long getVertexStoreForks() {
		return vertexStoreForks;
	}

	public long getTimeout() {
		return timeout;
	}

	public long getVertexStoreSize() {
		return vertexStoreSize;
	}

	public long getProcessed() {
		return processed;
	}

	public long getConsensusEvents() {
		return consensusEvents;
	}

	public long getIndirectParent() {
		return indirectParent;
	}

	public long getProposalsMade() {
		return proposalsMade;
	}

	public long getTimedOutViews() {
		return timedOutViews;
	}

	public long getTimeoutQuorums() {
		return timeoutQuorums;
	}

	public ConsensusDataSync getSync() {
		return sync;
	}
}
