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
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.FormedQC;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.PacemakerReducer;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
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
		private final ImmutableList<BFTNode> authors;
		private final List<HashCode> syncIds = new ArrayList<>();

		SyncRequestState(ImmutableList<BFTNode> authors) {
			this.authors = Objects.requireNonNull(authors);
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


	/**
	 * An asynchronous supplier which retrieves data for a vertex with a given id
	 */
	public interface SyncVerticesRequestSender {
		/**
		 * Send an RPC request to retrieve vertices given an Id and number of
		 * vertices. i.e. The vertex with the given id and (count - 1) ancestors
		 * will be returned.
		 *
		 * @param node the node to retrieve the vertex info from
		 * @param request get vertices request
		 */
		void sendGetVerticesRequest(BFTNode node, LocalGetVerticesRequest request);
	}

	private static final Logger log = LogManager.getLogger();
	private final VertexStore vertexStore;
	private final PacemakerReducer pacemakerReducer;
	private final Map<HashCode, SyncState> syncing = new HashMap<>();
	private final TreeMap<LedgerHeader, List<HashCode>> ledgerSyncing;
	private final Map<LocalGetVerticesRequest, SyncRequestState> bftSyncing = new HashMap<>();
	private final SyncVerticesRequestSender requestSender;
	private final EventDispatcher<LocalSyncRequest> localSyncRequestProcessor;
	private final ScheduledEventDispatcher<LocalGetVerticesRequest> timeoutDispatcher;
	private final Random random;
	private final int bftSyncPatienceMillis;
	private VerifiedLedgerHeaderAndProof currentLedgerHeader;

	public BFTSync(
		VertexStore vertexStore,
		PacemakerReducer pacemakerReducer,
		Comparator<LedgerHeader> ledgerHeaderComparator,
		SyncVerticesRequestSender requestSender,
		EventDispatcher<LocalSyncRequest> localSyncRequestProcessor,
		ScheduledEventDispatcher<LocalGetVerticesRequest> timeoutDispatcher,
		VerifiedLedgerHeaderAndProof currentLedgerHeader,
		Random random,
		int bftSyncPatienceMillis
	) {
		this.vertexStore = vertexStore;
		this.pacemakerReducer = pacemakerReducer;
		this.ledgerSyncing = new TreeMap<>(ledgerHeaderComparator);
		this.requestSender = requestSender;
		this.localSyncRequestProcessor = Objects.requireNonNull(localSyncRequestProcessor);
		this.timeoutDispatcher = Objects.requireNonNull(timeoutDispatcher);
		this.currentLedgerHeader = Objects.requireNonNull(currentLedgerHeader);
		this.random = random;
		this.bftSyncPatienceMillis = bftSyncPatienceMillis;
	}

	public EventProcessor<FormedQC> formedQCEventProcessor() {
		return formedQC -> {
			HighQC highQC = HighQC.from(formedQC.qc(), this.vertexStore.highQC().highestCommittedQC());
			syncToQC(highQC, formedQC.lastAuthor());
		};
	}

	@Override
	public SyncResult syncToQC(HighQC highQC, @Nullable BFTNode author) {
		final QuorumCertificate qc = highQC.highestQC();
		final HashCode vertexId = qc.getProposed().getVertexId();

		if (qc.getProposed().getView().compareTo(vertexStore.getRoot().getView()) < 0) {
			return SyncResult.INVALID;
		}

		if (vertexStore.addQC(qc)) {
			// TODO: check if already sent highest
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

		return SyncResult.IN_PROGRESS;
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

		this.sendBFTSyncRequest(syncState.highQC().highestQC().getProposed().getVertexId(), 1, authors, syncState.localSyncId);
	}

	private void doCommittedSync(SyncState syncState) {
		final HashCode committedQCId = syncState.highQC().highestCommittedQC().getProposed().getVertexId();
		syncState.setSyncStage(SyncStage.GET_COMMITTED_VERTICES);
		log.debug("SYNC_VERTICES: Committed: Sending initial GetVerticesRequest for sync={}", syncState);
		// Retrieve the 3 vertices preceding the committedQC so we can create a valid committed root

		ImmutableList<BFTNode> authors = Stream.concat(
			Stream.of(syncState.author),
			syncState.highQC().highestCommittedQC().getSigners().filter(n -> !n.equals(syncState.author))
		).collect(ImmutableList.toImmutableList());

		this.sendBFTSyncRequest(committedQCId, 3, authors, syncState.localSyncId);
	}

	public void processGetVerticesLocalTimeout(LocalGetVerticesRequest request) {
		SyncRequestState syncRequestState = bftSyncing.remove(request);
		if (syncRequestState == null) {
			return;
		}

		List<HashCode> syncIds = syncRequestState.syncIds.stream().filter(syncing::containsKey).collect(Collectors.toList());
		for (HashCode syncId : syncIds) {
			SyncState syncState = syncing.get(syncId);

			// Retry full sync on timeout
			int nextIndex = random.nextInt(syncRequestState.authors.size());
			BFTNode nextNode = syncRequestState.authors.get(nextIndex);
			syncing.remove(syncId);
			syncToQC(syncState.highQC, nextNode);
		}
	}

	private void sendBFTSyncRequest(HashCode vertexId, int count, ImmutableList<BFTNode> authors, HashCode syncId) {
		LocalGetVerticesRequest request = new LocalGetVerticesRequest(vertexId, count);
		SyncRequestState syncRequestState = bftSyncing.getOrDefault(request, new SyncRequestState(authors));
		if (syncRequestState.syncIds.isEmpty()) {
			this.timeoutDispatcher.dispatch(request, bftSyncPatienceMillis);
			this.requestSender.sendGetVerticesRequest(authors.get(0), request);
			this.bftSyncing.put(request, syncRequestState);
		}
		syncRequestState.syncIds.add(syncId);
	}

	private void rebuildAndSyncQC(SyncState syncState) {
		log.debug("SYNC_STATE: Rebuilding and syncing QC: sync={} curRoot={}", syncState, vertexStore.getRoot());

		// TODO: check if there are any vertices which haven't been local sync processed yet
		if (requiresLedgerSync(syncState)) {
			syncState.fetched.sort(Comparator.comparing(VerifiedVertex::getView));
			List<VerifiedVertex> nonRootVertices = syncState.fetched.stream().skip(1).collect(Collectors.toList());
			vertexStore.rebuild(
				syncState.fetched.get(0),
				syncState.fetched.get(1).getQC(),
				syncState.highQC().highestCommittedQC(),
				nonRootVertices
			);
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

		ImmutableList<BFTNode> signers = ImmutableList.of(syncState.author);
		syncState.fetched.addAll(response.getVertices());

		// TODO: verify actually extends rather than just state version comparison
		if (syncState.committedProof.getStateVersion() <= this.currentLedgerHeader.getStateVersion()) {
			rebuildAndSyncQC(syncState);
		} else {
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
			// TODO: combine
			for (VerifiedVertex v: syncState.fetched) {
				if (!vertexStore.addQC(v.getQC())) {
					log.info("GET_VERTICES failed: {}", syncState.highQC);
					return;
				}

				vertexStore.insertVertex(v);
			}

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

			this.sendBFTSyncRequest(parentId, 1, authors, syncState.localSyncId);
		}
	}

	@Override
	public void processGetVerticesErrorResponse(GetVerticesErrorResponse response) {
		// TODO: check response

		log.debug("SYNC_VERTICES: Received GetVerticesErrorResponse: {} highQC: {}", response, vertexStore.highQC());

		if (response.highQC().highestQC().getView().compareTo(vertexStore.highQC().highestQC().getView()) > 0) {
			// error response indicates that the node has moved on from last sync so try and sync to a new sync
			this.syncToQC(response.highQC(), response.getSender());
		}
	}

	@Override
	public void processGetVerticesResponse(GetVerticesResponse response) {
		// TODO: check response

		log.debug("SYNC_VERTICES: Received GetVerticesResponse {}", response);

		VerifiedVertex firstVertex = response.getVertices().get(0);
		LocalGetVerticesRequest requestInfo = new LocalGetVerticesRequest(firstVertex.getId(), response.getVertices().size());
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

	public void processBFTUpdate(BFTUpdate update) {
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
	}
}
