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

package com.radixdlt.consensus.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.BFTHeader;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncer;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VerifiedVertexChain;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedQC;
import com.radixdlt.consensus.bft.ViewVotingResult.FormedTC;
import com.radixdlt.consensus.liveness.PacemakerReducer;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manages keeping the VertexStore and pacemaker in sync for consensus
 */
public final class BFTSync implements BFTSyncResponseProcessor, BFTSyncer, LedgerUpdateProcessor<LedgerUpdate> {
	private enum SyncStage {
		PREPARING,
		GET_COMMITTED_VERTICES,
		SYNC_TO_COMMIT,
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
		private final VerifiedLedgerHeaderAndProof committedProof;
		private final BFTNode author;
		private SyncStage syncStage;
		private final LinkedList<VerifiedVertex> fetched = new LinkedList<>();

		SyncState(HighQC highQC, BFTNode author) {
			this.localSyncId = highQC.highestQC().getProposed().getVertexId();
			Pair<BFTHeader, VerifiedLedgerHeaderAndProof> pair = highQC.highestCommittedQC().getCommittedAndLedgerStateProof()
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
			return String.format("%s{%s syncState=%s}", this.getClass().getSimpleName(), highQC, syncStage);
		}
	}

	private static final Logger log = LogManager.getLogger();
	private final BFTNode self;
	private final VertexStore vertexStore;
	private final PacemakerReducer pacemakerReducer;
	private final Map<HashCode, SyncState> syncing = new HashMap<>();
	private final TreeMap<LedgerHeader, List<HashCode>> ledgerSyncing;
	private final Map<GetVerticesRequest, SyncRequestState> bftSyncing = new HashMap<>();
	private final RemoteEventDispatcher<GetVerticesRequest> requestSender;
	private final EventDispatcher<LocalSyncRequest> localSyncRequestProcessor;
	private final ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher;
	private final Random random;
	private final int bftSyncPatienceMillis;
	private final SystemCounters systemCounters;
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;

	public BFTSync(
		@Self BFTNode self,
		VertexStore vertexStore,
		PacemakerReducer pacemakerReducer,
		Comparator<LedgerHeader> ledgerHeaderComparator,
		RemoteEventDispatcher<GetVerticesRequest> requestSender,
		EventDispatcher<LocalSyncRequest> localSyncRequestProcessor,
		ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
		VerifiedLedgerHeaderAndProof currentLedgerHeader,
		Random random,
		int bftSyncPatienceMillis,
		SystemCounters systemCounters
	) {
		this.self = self;
		this.vertexStore = vertexStore;
		this.pacemakerReducer = pacemakerReducer;
		this.ledgerSyncing = new TreeMap<>(ledgerHeaderComparator);
		this.requestSender = requestSender;
		this.localSyncRequestProcessor = Objects.requireNonNull(localSyncRequestProcessor);
		this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
		this.currentLedgerHeader = Objects.requireNonNull(currentLedgerHeader);
		this.random = random;
		this.bftSyncPatienceMillis = bftSyncPatienceMillis;
		this.systemCounters = Objects.requireNonNull(systemCounters);
	}

	public EventProcessor<ViewQuorumReached> viewQuorumReachedEventProcessor() {
		return viewQuorumReached -> {
			final HighQC highQC;
			if (viewQuorumReached.votingResult() instanceof FormedQC) {
				highQC = HighQC.from(
						((FormedQC) viewQuorumReached.votingResult()).getQC(),
						this.vertexStore.highQC().highestCommittedQC(),
						this.vertexStore.getHighestTimeoutCertificate());
			} else if (viewQuorumReached.votingResult() instanceof FormedTC) {
				highQC = HighQC.from(
						this.vertexStore.highQC().highestQC(),
						this.vertexStore.highQC().highestCommittedQC(),
						Optional.of(((FormedTC) viewQuorumReached.votingResult()).getTC()));
			} else {
				throw new IllegalArgumentException("Unknown voting result: " + viewQuorumReached.votingResult());
			}

			syncToQC(highQC, viewQuorumReached.lastAuthor());
		};
	}

	@Override
	public SyncResult syncToQC(HighQC highQC, @Nullable BFTNode author) {
		final QuorumCertificate qc = highQC.highestQC();
		final HashCode vertexId = qc.getProposed().getVertexId();

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

		if (syncing.containsKey(vertexId)) {
			return SyncResult.IN_PROGRESS;
		}

		if (author == null) {
			throw new IllegalStateException("Syncing required but author wasn't provided.");
		}

		startSync(highQC, author);

		return SyncResult.STARTED;
	}

	private boolean requiresLedgerSync(SyncState syncState) {
		final BFTHeader committedHeader = syncState.committedHeader;
		if (!vertexStore.containsVertex(committedHeader.getVertexId())) {
			View rootView = vertexStore.getRoot().getView();
			return rootView.compareTo(committedHeader.getView()) < 0;
		}

		return false;
	}

	private void startSync(HighQC highQC, BFTNode author) {
		final SyncState syncState = new SyncState(highQC, author);
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
		ImmutableList<BFTNode> authors = Stream.concat(
			Stream.of(syncState.author),
			syncState.highQC().highestQC().getSigners().filter(n -> !n.equals(syncState.author))
		).collect(ImmutableList.toImmutableList());

		final var qc = syncState.highQC().highestQC();
		this.sendBFTSyncRequest(qc.getView(), qc.getProposed().getVertexId(), 1, authors, syncState.localSyncId);
	}

	private void doCommittedSync(SyncState syncState) {
		final HashCode committedQCId = syncState.highQC().highestCommittedQC().getProposed().getVertexId();
		final var commitedView = syncState.highQC().highestCommittedQC().getView();
		syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
		log.debug("SYNC_VERTICES: Committed: Sending initial GetVerticesRequest for sync={}", syncState);
		// Retrieve the 3 vertices preceding the committedQC so we can create a valid committed root

		ImmutableList<BFTNode> authors = Stream.concat(
			Stream.of(syncState.author),
			syncState.highQC().highestCommittedQC().getSigners().filter(n -> !n.equals(syncState.author))
		).collect(ImmutableList.toImmutableList());

		this.sendBFTSyncRequest(commitedView, committedQCId, 3, authors, syncState.localSyncId);
	}


	public EventProcessor<VertexRequestTimeout> vertexRequestTimeoutEventProcessor() {
		return this::processGetVerticesLocalTimeout;
	}

	private void processGetVerticesLocalTimeout(VertexRequestTimeout timeout) {
		final GetVerticesRequest request = highestQCRequest(this.bftSyncing.entrySet());

		SyncRequestState syncRequestState = bftSyncing.remove(request);
		if (syncRequestState == null) {
			return;
		}

		var authors = syncRequestState.authors.stream()
			.filter(author -> !author.equals(self)).collect(ImmutableList.toImmutableList());

		if (authors.isEmpty()) {
			throw new IllegalStateException("Request contains no authors except ourselves");
		}

		var syncIds = syncRequestState.syncIds.stream()
			.filter(syncing::containsKey).collect(Collectors.toList());

		//noinspection UnstableApiUsage
		for (var syncId : syncIds) {
			systemCounters.increment(CounterType.BFT_SYNC_REQUEST_TIMEOUTS);
			SyncState syncState = syncing.remove(syncId);
			syncToQC(syncState.highQC, randomFrom(authors));
		}
	}

	private GetVerticesRequest highestQCRequest(Collection<Map.Entry<GetVerticesRequest, SyncRequestState>> requests) {
		return requests.stream()
			.sorted(this::requestOrdering)
			.findFirst()
			.map(Map.Entry::getKey)
			.orElse(null);
	}

	private int requestOrdering(Map.Entry<GetVerticesRequest, SyncRequestState> left, Map.Entry<GetVerticesRequest, SyncRequestState> right) {
		return left.getValue().view.compareTo(right.getValue().view);
	}

	private <T> T randomFrom(List<T> elements) {
		final var size = elements.size();
		if (size <= 0) {
			return null;
		}
		int nextIndex = random.nextInt(size);
		return elements.get(nextIndex);
	}

	private void sendBFTSyncRequest(View view, HashCode vertexId, int count, ImmutableList<BFTNode> authors, HashCode syncId) {
		GetVerticesRequest request = new GetVerticesRequest(vertexId, count);
		SyncRequestState syncRequestState = bftSyncing.getOrDefault(request, new SyncRequestState(authors, view));
		if (syncRequestState.syncIds.isEmpty()) {
			VertexRequestTimeout scheduledTimeout = VertexRequestTimeout.create(request);
			this.timeoutDispatcher.dispatch(scheduledTimeout, bftSyncPatienceMillis);
			this.requestSender.dispatch(authors.get(0), request);
			this.bftSyncing.put(request, syncRequestState);
		}
		syncRequestState.syncIds.add(syncId);
	}

	private void rebuildAndSyncQC(SyncState syncState) {
		log.debug("SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}", syncState, vertexStore.getRoot());

		// TODO: check if there are any vertices which haven't been local sync processed yet
		if (requiresLedgerSync(syncState)) {
			syncState.fetched.sort(Comparator.comparing(VerifiedVertex::getView));
			ImmutableList<VerifiedVertex> nonRootVertices = syncState.fetched.stream()
				.skip(1)
				.collect(ImmutableList.toImmutableList());
			VerifiedVertexStoreState vertexStoreState = VerifiedVertexStoreState.create(
				HighQC.from(syncState.highQC().highestCommittedQC()),
				syncState.fetched.get(0),
				nonRootVertices,
				vertexStore.getHighestTimeoutCertificate()
			);
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

	private void processVerticesResponseForCommittedSync(SyncState syncState, GetVerticesResponse response) {
		log.debug("SYNC_STATE: Processing vertices {} View {} From {} CurrentLedgerHeader {}",
			syncState, response.getVertices().get(0).getView(), response.getSender(), this.currentLedgerHeader
		);

		syncState.fetched.addAll(response.getVertices());

		// TODO: verify actually extends rather than just state version comparison
		if (syncState.committedProof.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
			rebuildAndSyncQC(syncState);
		} else {
			ImmutableList<BFTNode> signers = ImmutableList.of(syncState.author);
			syncState.setSyncStage(SyncStage.SYNC_TO_COMMIT);
			ledgerSyncing.compute(syncState.committedProof.getRaw(), (header, syncing) -> {
				if (syncing == null) {
					syncing = new ArrayList<>();
				}
				syncing.add(syncState.localSyncId);
				return syncing;
			});
			LocalSyncRequest localSyncRequest = new LocalSyncRequest(
				syncState.committedProof,
				signers
			);
			localSyncRequestProcessor.dispatch(localSyncRequest);
		}
	}

	private void processVerticesResponseForQCSync(SyncState syncState, GetVerticesResponse response) {
		VerifiedVertex vertex = response.getVertices().get(0);
		syncState.fetched.addFirst(vertex);
		HashCode parentId = vertex.getParentId();

		if (vertexStore.containsVertex(parentId)) {
			vertexStore.insertVertexChain(VerifiedVertexChain.create(syncState.fetched));
			// Finish it off
			this.syncing.remove(syncState.localSyncId);
			this.syncToQC(syncState.highQC, syncState.author);
		} else {
			log.debug("SYNC_VERTICES: Sending further GetVerticesRequest for {} fetched={} root={}",
				syncState.highQC(), syncState.fetched.size(), vertexStore.getRoot());

			ImmutableList<BFTNode> authors = Stream.concat(
				Stream.of(syncState.author),
				vertex.getQC().getSigners().filter(n -> !n.equals(syncState.author))
			).collect(ImmutableList.toImmutableList());

			this.sendBFTSyncRequest(syncState.highQC.highestQC().getView(), parentId, 1, authors, syncState.localSyncId);
		}
	}

	@Override
	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		// TODO: check response
		final var request = response.request();
		final var syncRequestState = bftSyncing.get(request);
		if (syncRequestState != null) {
			log.debug("SYNC_VERTICES: Received GetVerticesErrorResponse: {} highQC: {}", response, vertexStore.highQC());
			if (response.highQC().highestQC().getView().compareTo(vertexStore.highQC().highestQC().getView()) > 0) {
				// error response indicates that the node has moved on from last sync so try and sync to a new sync
				if (SyncResult.STARTED == syncToQC(response.highQC(), response.getSender())) {
					this.bftSyncing.remove(request);
				}
			}
		}
	}

	public EventProcessor<GetVerticesResponse> responseProcessor() {
		return this::processGetVerticesResponse;
	}

	private void processGetVerticesResponse(GetVerticesResponse response) {
		// TODO: check response

		log.debug("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		VerifiedVertex firstVertex = response.getVertices().get(0);
		GetVerticesRequest requestInfo = new GetVerticesRequest(firstVertex.getId(), response.getVertices().size());
		SyncRequestState syncRequestState = bftSyncing.remove(requestInfo);
		if (syncRequestState != null) {
			for (HashCode syncTo : syncRequestState.syncIds) {
				SyncState syncState = syncing.get(syncTo);
				if (syncState == null) {
					continue; // sync requirements already satisfied by another sync
				}
				switch (syncState.syncStage) {
					case GET_COMMITTED_VERTICES:
						processVerticesResponseForCommittedSync(syncState, response);
						break;
					case GET_QC_VERTICES:
						processVerticesResponseForQCSync(syncState, response);
						break;
					default:
						throw new IllegalStateException("Unknown sync stage: " + syncState.syncStage);
				}
			}
		}
	}

	public void processBFTUpdate(BFTInsertUpdate update) {
	}

	// TODO: Verify headers match
	@Override
	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		log.trace("SYNC_STATE: update {}", ledgerUpdate.getTail());

		this.currentLedgerHeader = ledgerUpdate.getTail();

		Collection<List<HashCode>> listeners = this.ledgerSyncing.headMap(
			ledgerUpdate.getTail().getRaw(), true
		).values();
		Iterator<List<HashCode>> listenersIterator = listeners.iterator();
		while (listenersIterator.hasNext()) {
			List<HashCode> syncs = listenersIterator.next();
			for (HashCode syncTo : syncs) {
				SyncState syncState = syncing.get(syncTo);
				if (syncState != null) {
					rebuildAndSyncQC(syncState);
				}
			}
			listenersIterator.remove();
		}

		syncing.values().removeIf(state -> state.highQC.highestQC().getView().lte(ledgerUpdate.getTail().getView()));
	}
}