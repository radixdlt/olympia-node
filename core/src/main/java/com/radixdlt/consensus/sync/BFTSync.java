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

package com.radixdlt.consensus.sync;

import static java.util.function.Predicate.not;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.*;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedQC;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedTC;
import com.radixdlt.consensus.liveness.PacemakerReducer;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Manages keeping the VertexStore and pacemaker in sync for consensus */
public final class BFTSync implements BFTSyncer {
  private enum SyncStage {
    PREPARING,
    GET_COMMITTED_VERTICES,
    LEDGER_SYNC,
    GET_QC_VERTICES
  }

  private static class SyncRequestState {
    private final List<HashCode> syncIds = new ArrayList<>();
    private final ImmutableList<BFTNode> authors;
    private final View view;

    SyncRequestState(ImmutableList<BFTNode> authors, View view) {
      this.authors = Objects.requireNonNull(authors);
      this.view = Objects.requireNonNull(view);
    }
  }

  private static class SyncState {
    private final HashCode localSyncId;
    private final HighQC highQC;
    private final BFTHeader committedHeader;
    private final LedgerProof committedProof;
    private final BFTNode author;
    private SyncStage syncStage;
    private final LinkedList<VerifiedVertex> fetched = new LinkedList<>();

    SyncState(HighQC highQC, BFTNode author, Hasher hasher) {
      this.localSyncId = highQC.highestQC().getProposed().getVertexId();
      var pair =
          highQC
              .highestCommittedQC()
              .getCommittedAndLedgerStateProof(hasher)
              .orElseThrow(() -> new IllegalStateException("committedQC must have a commit"));
      this.committedHeader = pair.getFirst();
      this.committedProof = pair.getSecond();
      this.highQC = highQC;
      this.author = author;
      this.syncStage = SyncStage.PREPARING;
    }

    void setSyncStage(SyncStage syncStage) {
      this.syncStage = syncStage;
    }

    HighQC highQC() {
      return this.highQC;
    }

    @Override
    public String toString() {
      return String.format(
          "%s{%s syncState=%s}", this.getClass().getSimpleName(), highQC, syncStage);
    }
  }

  private static final Comparator<Map.Entry<GetVerticesRequest, SyncRequestState>> syncPriority =
      Comparator.comparing((Map.Entry<GetVerticesRequest, SyncRequestState> e) -> e.getValue().view)
          .reversed(); // Prioritise by highest view

  private static final Logger log = LogManager.getLogger();
  private final BFTNode self;
  private final VertexStore vertexStore;
  private final Hasher hasher;
  private final PacemakerReducer pacemakerReducer;
  private final Map<HashCode, SyncState> syncing = new HashMap<>();
  private final TreeMap<LedgerHeader, List<HashCode>> ledgerSyncing;
  private final Map<GetVerticesRequest, SyncRequestState> bftSyncing = new HashMap<>();
  private final RemoteEventDispatcher<GetVerticesRequest> requestSender;
  private final EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher;
  private final ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher;
  private final Random random;
  private final int bftSyncPatienceMillis;
  private final SystemCounters systemCounters;
  private LedgerProof currentLedgerHeader;

  // TODO: remove once we figure that out
  private final Set<String> runOnThreads = Collections.newSetFromMap(new ConcurrentHashMap<>(2));

  // FIXME: Remove this once sync is fixed
  private final RateLimiter syncRequestRateLimiter;

  public BFTSync(
      @Self BFTNode self,
      RateLimiter syncRequestRateLimiter,
      VertexStore vertexStore,
      Hasher hasher,
      PacemakerReducer pacemakerReducer,
      Comparator<LedgerHeader> ledgerHeaderComparator,
      RemoteEventDispatcher<GetVerticesRequest> requestSender,
      EventDispatcher<LocalSyncRequest> localSyncRequestEventDispatcher,
      ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
      LedgerProof currentLedgerHeader,
      Random random,
      int bftSyncPatienceMillis,
      SystemCounters systemCounters) {
    this.self = self;
    this.syncRequestRateLimiter = Objects.requireNonNull(syncRequestRateLimiter);
    this.vertexStore = vertexStore;
    this.hasher = Objects.requireNonNull(hasher);
    this.pacemakerReducer = pacemakerReducer;
    this.ledgerSyncing = new TreeMap<>(ledgerHeaderComparator);
    this.requestSender = requestSender;
    this.localSyncRequestEventDispatcher = Objects.requireNonNull(localSyncRequestEventDispatcher);
    this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
    this.currentLedgerHeader = Objects.requireNonNull(currentLedgerHeader);
    this.random = random;
    this.bftSyncPatienceMillis = bftSyncPatienceMillis;
    this.systemCounters = Objects.requireNonNull(systemCounters);
  }

  public EventProcessor<ViewQuorumReached> viewQuorumReachedEventProcessor() {
    return viewQuorumReached -> {
      this.runOnThreads.add(Thread.currentThread().getName());

      final var highQC =
          switch (viewQuorumReached.votingResult()) {
            case FormedQC formedQc -> HighQC.from(
                ((FormedQC) viewQuorumReached.votingResult()).getQC(),
                this.vertexStore.highQC().highestCommittedQC(),
                this.vertexStore.getHighestTimeoutCertificate());
            case FormedTC formedTc -> HighQC.from(
                this.vertexStore.highQC().highestQC(),
                this.vertexStore.highQC().highestCommittedQC(),
                Optional.of(((FormedTC) viewQuorumReached.votingResult()).getTC()));
          };

      syncToQC(highQC, viewQuorumReached.lastAuthor());
    };
  }

  @Override
  public SyncResult syncToQC(HighQC highQC, @Nullable BFTNode author) {
    this.runOnThreads.add(Thread.currentThread().getName());
    final var qc = highQC.highestQC();

    if (qc.getProposed().getView().compareTo(vertexStore.getRoot().getView()) < 0) {
      return SyncResult.INVALID;
    }

    if (qc.getProposed().getView().compareTo(this.currentLedgerHeader.getView()) < 0) {
      return SyncResult.INVALID;
    }

    highQC.highestTC().ifPresent(vertexStore::insertTimeoutCertificate);

    if (vertexStore.addQC(qc)) {
      // TODO: check if already sent highest
      // TODO: Move pacemaker outside of sync
      this.pacemakerReducer.processQC(vertexStore.highQC());
      return SyncResult.SYNCED;
    }

    // TODO: Move this check into pre-check
    // Bad genesis qc, ignore...
    if (qc.getView().isGenesis()) {
      log.warn("SYNC_TO_QC: Bad Genesis: {}", highQC);
      return SyncResult.INVALID;
    }

    log.trace("SYNC_TO_QC: Need sync: {}", highQC);

    if (syncing.containsKey(qc.getProposed().getVertexId())) {
      return SyncResult.IN_PROGRESS;
    }

    if (author == null) {
      throw new IllegalStateException("Syncing required but author wasn't provided.");
    }

    startSync(highQC, author);

    return SyncResult.IN_PROGRESS;
  }

  private boolean requiresLedgerSync(SyncState syncState) {
    final var committedHeader = syncState.committedHeader;

    if (!vertexStore.containsVertex(committedHeader.getVertexId())) {
      var rootView = vertexStore.getRoot().getView();
      return rootView.compareTo(committedHeader.getView()) < 0;
    }

    return false;
  }

  private void startSync(HighQC highQC, BFTNode author) {
    final var syncState = new SyncState(highQC, author, hasher);

    syncing.put(syncState.localSyncId, syncState);

    if (requiresLedgerSync(syncState)) {
      this.doCommittedSync(syncState);
    } else {
      this.doQCSync(syncState);
    }
  }

  private void doQCSync(SyncState syncState) {
    syncState.setSyncStage(SyncStage.GET_QC_VERTICES);
    log.debug("SYNC_VERTICES: QC: Sending initial GetVerticesRequest for sync={}", syncState);

    final var authors =
        Stream.concat(
                Stream.of(syncState.author),
                syncState
                    .highQC()
                    .highestQC()
                    .getSigners()
                    .filter(n -> !n.equals(syncState.author)))
            .filter(not(n -> n.equals(this.self)))
            .collect(ImmutableList.toImmutableList());

    final var qc = syncState.highQC().highestQC();
    this.sendBFTSyncRequest(
        qc.getView(), qc.getProposed().getVertexId(), 1, authors, syncState.localSyncId);
  }

  private void doCommittedSync(SyncState syncState) {
    final var committedQCId = syncState.highQC().highestCommittedQC().getProposed().getVertexId();
    final var commitedView = syncState.highQC().highestCommittedQC().getView();

    syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
    log.debug(
        "SYNC_VERTICES: Committed: Sending initial GetVerticesRequest for sync={}", syncState);
    // Retrieve the 3 vertices preceding the committedQC so we can create a valid committed root

    final var authors =
        Stream.concat(
                Stream.of(syncState.author),
                syncState
                    .highQC()
                    .highestCommittedQC()
                    .getSigners()
                    .filter(n -> !n.equals(syncState.author)))
            .filter(not(n -> n.equals(this.self)))
            .collect(ImmutableList.toImmutableList());

    this.sendBFTSyncRequest(commitedView, committedQCId, 3, authors, syncState.localSyncId);
  }

  public EventProcessor<VertexRequestTimeout> vertexRequestTimeoutEventProcessor() {
    return this::processGetVerticesLocalTimeout;
  }

  private void processGetVerticesLocalTimeout(VertexRequestTimeout timeout) {
    this.runOnThreads.add(Thread.currentThread().getName());

    final var request = highestQCRequest(this.bftSyncing.entrySet());
    var syncRequestState = bftSyncing.remove(request);

    if (syncRequestState == null) {
      return;
    }

    if (syncRequestState.authors.isEmpty()) {
      throw new IllegalStateException("Request contains no authors except ourselves");
    }

    var syncIds =
        syncRequestState.syncIds.stream().filter(syncing::containsKey).distinct().toList();

    //noinspection UnstableApiUsage
    for (var syncId : syncIds) {
      systemCounters.increment(CounterType.BFT_SYNC_REQUEST_TIMEOUTS);
      var syncState = syncing.remove(syncId);

      if (syncState == null) {
        // TODO: remove once we figure this out
        final var msg = new StringBuilder();
        msg.append("Got a null value from \"syncing\" map. SyncId=")
            .append(syncId)
            .append(" SyncIds=")
            .append(syncIds)
            .append(" Map=")
            .append(syncing)
            .append(" Contains=")
            .append(syncing.containsKey(syncId))
            .append(" Thread=")
            .append(Thread.currentThread().getName())
            .append(" OtherThreads=")
            .append(String.join(",", runOnThreads));
        log.error(msg.toString());
        throw new IllegalStateException(
            "Inconsistent sync state, please contact Radix team member on Discord. (" + msg + ")");
      } else {
        syncToQC(syncState.highQC, randomFrom(syncRequestState.authors));
      }
    }
  }

  private GetVerticesRequest highestQCRequest(
      Collection<Map.Entry<GetVerticesRequest, SyncRequestState>> requests) {
    return requests.stream().sorted(syncPriority).findFirst().map(Map.Entry::getKey).orElse(null);
  }

  private <T> T randomFrom(List<T> elements) {
    final var size = elements.size();

    if (size <= 0) {
      return null;
    }

    return elements.get(random.nextInt(size));
  }

  private void sendBFTSyncRequest(
      View view, HashCode vertexId, int count, ImmutableList<BFTNode> authors, HashCode syncId) {
    var request = new GetVerticesRequest(vertexId, count);
    var syncRequestState = bftSyncing.getOrDefault(request, new SyncRequestState(authors, view));

    if (syncRequestState.syncIds.isEmpty()) {
      if (this.syncRequestRateLimiter.tryAcquire()) {
        VertexRequestTimeout scheduledTimeout = VertexRequestTimeout.create(request);
        this.timeoutDispatcher.dispatch(scheduledTimeout, bftSyncPatienceMillis);
        this.requestSender.dispatch(authors.get(0), request);
      } else {
        log.warn("RATE_LIMIT: Request dropped");
      }
      this.bftSyncing.put(request, syncRequestState);
    }
    syncRequestState.syncIds.add(syncId);
  }

  private void rebuildAndSyncQC(SyncState syncState) {
    log.debug(
        "SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}",
        syncState,
        vertexStore.getRoot());

    // TODO: check if there are any vertices which haven't been local sync processed yet
    if (requiresLedgerSync(syncState)) {
      syncState.fetched.sort(Comparator.comparing(VerifiedVertex::getView));
      ImmutableList<VerifiedVertex> nonRootVertices =
          syncState.fetched.stream().skip(1).collect(ImmutableList.toImmutableList());
      var vertexStoreState =
          VerifiedVertexStoreState.create(
              HighQC.from(syncState.highQC().highestCommittedQC()),
              syncState.fetched.get(0),
              nonRootVertices,
              vertexStore.getHighestTimeoutCertificate(),
              hasher);
      if (vertexStore.tryRebuild(vertexStoreState)) {
        // TODO: Move pacemaker outside of sync
        pacemakerReducer.processQC(vertexStoreState.getHighQC());
      }
    } else {
      log.debug("SYNC_STATE: skipping rebuild");
    }

    // At this point we are guaranteed to be in sync with the committed state
    // Retry sync
    this.syncing.remove(syncState.localSyncId);
    this.syncToQC(syncState.highQC(), syncState.author);
  }

  private void processVerticesResponseForCommittedSync(
      SyncState syncState, BFTNode sender, GetVerticesResponse response) {
    log.debug(
        "SYNC_STATE: Processing vertices {} View {} From {} CurrentLedgerHeader {}",
        syncState,
        response.getVertices().get(0).getView(),
        sender,
        this.currentLedgerHeader);

    syncState.fetched.addAll(response.getVertices());

    // TODO: verify actually extends rather than just state version comparison
    if (syncState.committedProof.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
      rebuildAndSyncQC(syncState);
    } else {
      syncState.setSyncStage(SyncStage.LEDGER_SYNC);
      ledgerSyncing.compute(
          syncState.committedProof.getRaw(),
          (header, existingList) -> {
            var list = (existingList == null) ? new ArrayList<HashCode>() : existingList;
            list.add(syncState.localSyncId);
            return list;
          });
      var signers = syncState.committedProof.getSignersWithout(self);
      var localSyncRequest = new LocalSyncRequest(syncState.committedProof, signers);

      localSyncRequestEventDispatcher.dispatch(localSyncRequest);
    }
  }

  private void processVerticesResponseForQCSync(SyncState syncState, GetVerticesResponse response) {
    var vertex = response.getVertices().get(0);
    syncState.fetched.addFirst(vertex);

    var parentId = vertex.getParentId();

    if (vertexStore.containsVertex(parentId)) {
      vertexStore.insertVertexChain(VerifiedVertexChain.create(syncState.fetched));
      // Finish it off
      this.syncing.remove(syncState.localSyncId);
      this.syncToQC(syncState.highQC, syncState.author);
    } else {
      log.debug(
          "SYNC_VERTICES: Sending further GetVerticesRequest for {} fetched={} root={}",
          syncState.highQC(),
          syncState.fetched.size(),
          vertexStore.getRoot());

      final var authors =
          Stream.concat(
                  Stream.of(syncState.author),
                  vertex.getQC().getSigners().filter(n -> !n.equals(syncState.author)))
              .filter(not(n -> n.equals(this.self)))
              .collect(ImmutableList.toImmutableList());

      this.sendBFTSyncRequest(
          syncState.highQC.highestQC().getView(), parentId, 1, authors, syncState.localSyncId);
    }
  }

  private void processGetVerticesErrorResponse(BFTNode sender, GetVerticesErrorResponse response) {
    this.runOnThreads.add(Thread.currentThread().getName());

    // TODO: check response
    final var request = response.request();
    final var syncRequestState = bftSyncing.get(request);
    if (syncRequestState != null) {
      log.debug(
          "SYNC_VERTICES: Received GetVerticesErrorResponse: {} highQC: {}",
          response,
          vertexStore.highQC());
      if (response
              .highQC()
              .highestQC()
              .getView()
              .compareTo(vertexStore.highQC().highestQC().getView())
          > 0) {
        // error response indicates that the node has moved on from last sync so try and sync to a
        // new qc
        syncToQC(response.highQC(), sender);
      }
    }
  }

  public RemoteEventProcessor<GetVerticesErrorResponse> errorResponseProcessor() {
    return this::processGetVerticesErrorResponse;
  }

  public RemoteEventProcessor<GetVerticesResponse> responseProcessor() {
    return this::processGetVerticesResponse;
  }

  private void processGetVerticesResponse(BFTNode sender, GetVerticesResponse response) {
    this.runOnThreads.add(Thread.currentThread().getName());

    // TODO: check response

    log.debug("SYNC_VERTICES: Received GetVerticesResponse {}", response);

    var firstVertex = response.getVertices().get(0);
    var requestInfo = new GetVerticesRequest(firstVertex.getId(), response.getVertices().size());
    var syncRequestState = bftSyncing.remove(requestInfo);

    if (syncRequestState != null) {
      for (var syncTo : syncRequestState.syncIds) {
        var syncState = syncing.get(syncTo);
        if (syncState == null) {
          continue; // sync requirements already satisfied by another sync
        }

        switch (syncState.syncStage) {
          case GET_COMMITTED_VERTICES -> processVerticesResponseForCommittedSync(
              syncState, sender, response);
          case GET_QC_VERTICES -> processVerticesResponseForQCSync(syncState, response);
          default -> throw new IllegalStateException("Unknown sync stage: " + syncState.syncStage);
        }
      }
    }
  }

  public EventProcessor<LedgerUpdate> baseLedgerUpdateEventProcessor() {
    return this::processLedgerUpdate;
  }

  // TODO: Verify headers match
  private void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
    this.runOnThreads.add(Thread.currentThread().getName());

    log.trace("SYNC_STATE: update {}", ledgerUpdate.getTail());

    this.currentLedgerHeader = ledgerUpdate.getTail();

    var listeners = this.ledgerSyncing.headMap(ledgerUpdate.getTail().getRaw(), true).values();
    var listenersIterator = listeners.iterator();

    while (listenersIterator.hasNext()) {
      var syncs = listenersIterator.next();
      for (var syncTo : syncs) {

        var syncState = syncing.get(syncTo);
        if (syncState != null) {
          rebuildAndSyncQC(syncState);
        }
      }
      listenersIterator.remove();
    }

    syncing
        .entrySet()
        .removeIf(
            e -> e.getValue().highQC.highestQC().getView().lte(ledgerUpdate.getTail().getView()));
  }
}
