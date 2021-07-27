/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ConsensusData {
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
