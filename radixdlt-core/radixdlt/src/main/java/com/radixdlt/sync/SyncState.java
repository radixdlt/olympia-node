/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.sync;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.sync.messages.remote.StatusResponse;

import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;

/**
 * The current state of the local sync service.
 * There are 3 possible states:
 * - idle: the service is not waiting for any response; it only processes local ledger updates and sync requests
 * - sync check: the service is waiting for StatusResponses; it also processes local messages and timeouts
 * - syncing: the service is waiting for SyncResponse; it also processes local messages and timeouts
 */
public interface SyncState {

	/**
	 * Gets the current header.
	 */
	LedgerProof getCurrentHeader();

	/**
	 * Returns a SyncState with a new current header.
	 */
	SyncState withCurrentHeader(LedgerProof newCurrentHeader);

	final class IdleState implements SyncState {
		private final LedgerProof currentHeader;

		public static IdleState init(LedgerProof currentHeader) {
			return new IdleState(currentHeader);
		}

		private IdleState(LedgerProof currentHeader) {
			this.currentHeader = currentHeader;
		}

		@Override
		public LedgerProof getCurrentHeader() {
			return this.currentHeader;
		}

		@Override
		public IdleState withCurrentHeader(LedgerProof newCurrentHeader) {
			return new IdleState(newCurrentHeader);
		}

		@Override
		public String toString() {
			return String.format("%s{currentHeader=%s}", this.getClass().getSimpleName(), this.currentHeader);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			IdleState idleState = (IdleState) o;
			return Objects.equals(currentHeader, idleState.currentHeader);
		}

		@Override
		public int hashCode() {
			return Objects.hash(currentHeader);
		}
	}

	final class SyncCheckState implements SyncState {
		private final LedgerProof currentHeader;
		private final ImmutableSet<BFTNode> peersAskedForStatus;
		private final ImmutableMap<BFTNode, StatusResponse> receivedStatusResponses;

		public static SyncCheckState init(
			LedgerProof currentHeader,
			ImmutableSet<BFTNode> peersAskedForStatus
		) {
			return new SyncCheckState(
				currentHeader,
				peersAskedForStatus,
				ImmutableMap.of()
			);
		}

		private SyncCheckState(
			LedgerProof currentHeader,
			ImmutableSet<BFTNode> peersAskedForStatus,
			ImmutableMap<BFTNode, StatusResponse> receivedStatusResponses
		) {
			this.currentHeader = currentHeader;
			this.peersAskedForStatus = peersAskedForStatus;
			this.receivedStatusResponses = receivedStatusResponses;
		}

		public boolean hasAskedPeer(BFTNode peer) {
			return this.peersAskedForStatus.contains(peer);
		}

		public boolean receivedResponseFrom(BFTNode peer) {
			return this.receivedStatusResponses.containsKey(peer);
		}

		public boolean gotAllResponses() {
			return this.receivedStatusResponses.size() == this.peersAskedForStatus.size();
		}

		public ImmutableMap<BFTNode, StatusResponse> responses() {
			return this.receivedStatusResponses;
		}

		public SyncCheckState withStatusResponse(BFTNode peer, StatusResponse statusResponse) {
			return new SyncCheckState(
				currentHeader,
				peersAskedForStatus,
				new ImmutableMap.Builder<BFTNode, StatusResponse>()
					.putAll(receivedStatusResponses)
					.put(peer, statusResponse)
					.build()
			);
		}

		@Override
		public LedgerProof getCurrentHeader() {
			return this.currentHeader;
		}

		@Override
		public SyncCheckState withCurrentHeader(LedgerProof newCurrentHeader) {
			return new SyncCheckState(newCurrentHeader, peersAskedForStatus, receivedStatusResponses);
		}

		@Override
		public String toString() {
			return String.format("%s{currentHeader=%s peersAskedForStatus=%s receivedStatusResponses=%s}",
				this.getClass().getSimpleName(),
				this.currentHeader,
				this.peersAskedForStatus,
				this.receivedStatusResponses
			);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			SyncCheckState that = (SyncCheckState) o;
			return Objects.equals(currentHeader, that.currentHeader)
				&& Objects.equals(peersAskedForStatus, that.peersAskedForStatus)
				&& Objects.equals(receivedStatusResponses, that.receivedStatusResponses);
		}

		@Override
		public int hashCode() {
			return Objects.hash(currentHeader, peersAskedForStatus, receivedStatusResponses);
		}
	}

	final class PendingRequest {
		private final BFTNode peer;
		private final long requestId;

		public static PendingRequest create(BFTNode peer, long requestId) {
			return new PendingRequest(peer, requestId);
		}

		private PendingRequest(BFTNode peer, long requestId) {
			this.peer = peer;
			this.requestId = requestId;
		}

		public BFTNode getPeer() {
			return peer;
		}

		public long getRequestId() {
			return requestId;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			final var that = (PendingRequest) o;
			return requestId == that.requestId && Objects.equals(peer, that.peer);
		}

		@Override
		public int hashCode() {
			return Objects.hash(peer, requestId);
		}
	}

	final class SyncingState implements SyncState {
		private final LedgerProof currentHeader;
		private final ImmutableList<BFTNode> candidatePeers;
		private final LedgerProof targetHeader;
		private final Optional<PendingRequest> pendingRequest;

		public static SyncingState init(
			LedgerProof currentHeader,
			ImmutableList<BFTNode> candidatePeers,
			LedgerProof targetHeader
		) {
			return new SyncingState(currentHeader, candidatePeers, targetHeader, Optional.empty());
		}

		private SyncingState(
			LedgerProof currentHeader,
			ImmutableList<BFTNode> candidatePeers,
			LedgerProof targetHeader,
			Optional<PendingRequest> pendingRequest
		) {
			this.currentHeader = currentHeader;
			this.candidatePeers = candidatePeers;
			this.targetHeader = targetHeader;
			this.pendingRequest = pendingRequest;
		}

		public SyncingState withPendingRequest(BFTNode peer, long requestId) {
			return new SyncingState(
				currentHeader,
				candidatePeers,
				targetHeader,
				Optional.of(PendingRequest.create(peer, requestId))
			);
		}

		public SyncingState clearPendingRequest() {
			return new SyncingState(currentHeader, candidatePeers, targetHeader, Optional.empty());
		}

		public SyncingState removeCandidate(BFTNode peer) {
			return new SyncingState(
				currentHeader,
				ImmutableList.copyOf(Collections2.filter(candidatePeers, not(equalTo(peer)))),
				targetHeader,
				pendingRequest
			);
		}

		public SyncingState withTargetHeader(LedgerProof newTargetHeader) {
			return new SyncingState(currentHeader, candidatePeers, newTargetHeader, pendingRequest);
		}

		public SyncingState withCandidatePeers(ImmutableList<BFTNode> peers) {
			return new SyncingState(
				currentHeader,
				new ImmutableList.Builder<BFTNode>()
					.addAll(peers)
					.addAll(candidatePeers)
					.build(),
				targetHeader,
				pendingRequest
			);
		}

		public boolean waitingForResponse() {
			return this.pendingRequest.isPresent();
		}

		public boolean waitingForResponseFrom(BFTNode peer) {
			return this.pendingRequest.stream().anyMatch(pr -> pr.getPeer().equals(peer));
		}

		public Optional<PendingRequest> getPendingRequest() {
			return this.pendingRequest;
		}

		public ImmutableList<BFTNode> candidatePeers() {
			return this.candidatePeers;
		}

		public LedgerProof getTargetHeader() {
			return this.targetHeader;
		}

		@Override
		public LedgerProof getCurrentHeader() {
			return this.currentHeader;
		}

		@Override
		public SyncingState withCurrentHeader(LedgerProof newCurrentHeader) {
			return new SyncingState(newCurrentHeader, candidatePeers, targetHeader, pendingRequest);
		}

		@Override
		public String toString() {
			return String.format("%s{currentHeader=%s targetHeader=%s}",
				getClass().getSimpleName(), currentHeader, targetHeader);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			SyncingState that = (SyncingState) o;
			return Objects.equals(currentHeader, that.currentHeader)
				&& Objects.equals(candidatePeers, that.candidatePeers)
				&& Objects.equals(targetHeader, that.targetHeader)
				&& Objects.equals(pendingRequest, that.pendingRequest);
		}

		@Override
		public int hashCode() {
			return Objects.hash(currentHeader, candidatePeers, targetHeader, pendingRequest);
		}
	}
}
