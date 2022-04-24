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

package com.radixdlt.sync;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.sync.messages.remote.StatusResponse;
import com.radixdlt.utils.Pair;
import java.util.Objects;
import java.util.Optional;

/**
 * The current state of the local sync service. There are 3 possible states: - idle: the service is
 * not waiting for any response; it only processes local ledger updates and sync requests - sync
 * check: the service is waiting for StatusResponses; it also processes local messages and timeouts
 * - syncing: the service is waiting for SyncResponse; it also processes local messages and timeouts
 */
public sealed interface SyncState {

  /** Gets the current header. */
  LedgerProof getCurrentHeader();

  /** Returns a SyncState with a new current header. */
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
      return String.format(
          "%s{currentHeader=%s}", this.getClass().getSimpleName(), this.currentHeader);
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
        LedgerProof currentHeader, ImmutableSet<BFTNode> peersAskedForStatus) {
      return new SyncCheckState(currentHeader, peersAskedForStatus, ImmutableMap.of());
    }

    private SyncCheckState(
        LedgerProof currentHeader,
        ImmutableSet<BFTNode> peersAskedForStatus,
        ImmutableMap<BFTNode, StatusResponse> receivedStatusResponses) {
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
              .build());
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
      return String.format(
          "%s{currentHeader=%s peersAskedForStatus=%s receivedStatusResponses=%s}",
          this.getClass().getSimpleName(),
          this.currentHeader,
          this.peersAskedForStatus,
          this.receivedStatusResponses);
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
    private final ImmutableList<BFTNode> candidatePeersQueue;
    private final LedgerProof targetHeader;
    private final Optional<PendingRequest> pendingRequest;

    public static SyncingState init(
        LedgerProof currentHeader,
        ImmutableList<BFTNode> candidatePeersQueue,
        LedgerProof targetHeader) {
      return new SyncingState(currentHeader, candidatePeersQueue, targetHeader, Optional.empty());
    }

    private SyncingState(
        LedgerProof currentHeader,
        ImmutableList<BFTNode> candidatePeersQueue,
        LedgerProof targetHeader,
        Optional<PendingRequest> pendingRequest) {
      this.currentHeader = currentHeader;
      this.candidatePeersQueue = candidatePeersQueue;
      this.targetHeader = targetHeader;
      this.pendingRequest = pendingRequest;
    }

    public SyncingState withPendingRequest(BFTNode peer, long requestId) {
      return new SyncingState(
          currentHeader,
          candidatePeersQueue,
          targetHeader,
          Optional.of(PendingRequest.create(peer, requestId)));
    }

    public SyncingState clearPendingRequest() {
      return new SyncingState(currentHeader, candidatePeersQueue, targetHeader, Optional.empty());
    }

    public SyncingState removeCandidate(BFTNode peer) {
      return new SyncingState(
          currentHeader,
          ImmutableList.copyOf(Collections2.filter(candidatePeersQueue, not(equalTo(peer)))),
          targetHeader,
          pendingRequest);
    }

    public SyncingState withTargetHeader(LedgerProof newTargetHeader) {
      return new SyncingState(currentHeader, candidatePeersQueue, newTargetHeader, pendingRequest);
    }

    public Pair<SyncingState, Optional<BFTNode>> fetchNextCandidatePeer() {
      final var peerToUse = candidatePeersQueue.stream().findFirst();

      if (peerToUse.isPresent()) {
        final var newState =
            new SyncingState(
                currentHeader,
                new ImmutableList.Builder<BFTNode>()
                    .addAll(Collections2.filter(candidatePeersQueue, not(equalTo(peerToUse.get()))))
                    .add(peerToUse.get())
                    .build(),
                targetHeader,
                pendingRequest);

        return Pair.of(newState, peerToUse);
      } else {
        return Pair.of(this, Optional.empty());
      }
    }

    public SyncingState addCandidatePeers(ImmutableList<BFTNode> peers) {
      return new SyncingState(
          currentHeader,
          new ImmutableList.Builder<BFTNode>()
              .addAll(peers)
              .addAll(Collections2.filter(candidatePeersQueue, not(peers::contains)))
              .build(),
          targetHeader,
          pendingRequest);
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

    public LedgerProof getTargetHeader() {
      return this.targetHeader;
    }

    public Optional<BFTNode> peekNthCandidate(int n) {
      var state = this;
      Optional<BFTNode> candidate = Optional.empty();
      for (int i = 0; i <= n; i++) {
        final var res = state.fetchNextCandidatePeer();
        state = res.getFirst();
        candidate = res.getSecond();
      }
      return candidate;
    }

    @Override
    public LedgerProof getCurrentHeader() {
      return this.currentHeader;
    }

    @Override
    public SyncingState withCurrentHeader(LedgerProof newCurrentHeader) {
      return new SyncingState(newCurrentHeader, candidatePeersQueue, targetHeader, pendingRequest);
    }

    @Override
    public String toString() {
      return String.format(
          "%s{currentHeader=%s targetHeader=%s}",
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
          && Objects.equals(candidatePeersQueue, that.candidatePeersQueue)
          && Objects.equals(targetHeader, that.targetHeader)
          && Objects.equals(pendingRequest, that.pendingRequest);
    }

    @Override
    public int hashCode() {
      return Objects.hash(currentHeader, candidatePeersQueue, targetHeader, pendingRequest);
    }
  }
}
