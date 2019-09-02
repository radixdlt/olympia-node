package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.consensus.messages.SampleRequestMessage;
import com.radixdlt.tempo.consensus.messages.SampleResponseMessage;
import com.radixdlt.tempo.store.SampleStore;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.TemporalProof;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class SampleRetriever implements Closeable {
	private static final Logger log = Logging.getLogger("consensus.sampler");
	private static final int REQUEST_QUEUE_CAPACITY = 8192;
	private static final int REQUEST_PROCESSOR_THREADS = 1;

	private final Scheduler scheduler;
	private final TempoAtomStoreView storeView;
	private final SampleStore sampleStore;
	private final MessageCentral messageCentral;

	private final SamplingState samplingState = new SamplingState();

	private final BlockingQueue<SampleRequest> requestQueue;
	private final SimpleThreadPool<SampleRequest> requestThreadPool;

	@Inject
	public SampleRetriever(
		Scheduler scheduler,
		TempoAtomStoreView storeView,
		SampleStore sampleStore,
		MessageCentral messageCentral
	) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.storeView = Objects.requireNonNull(storeView);
		this.sampleStore = Objects.requireNonNull(sampleStore);
		this.messageCentral = Objects.requireNonNull(messageCentral);

		messageCentral.addListener(SampleRequestMessage.class, this::onRequest);
		messageCentral.addListener(SampleResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY);
		this.requestThreadPool = new SimpleThreadPool<>("Sample request processing", REQUEST_PROCESSOR_THREADS, requestQueue::take, this::processRequest, log);
		this.requestThreadPool.start();
	}

	public CompletableFuture<SamplingResult> sample(Set<LedgerIndex> indices, Collection<Peer> peers) {
		EUID tag = allocateTag();
		SampleRequestMessage request = new SampleRequestMessage(ImmutableMap.of(tag, indices));
		for (Peer peer : peers) {
			messageCentral.send(peer, request);
		}
		// TODO implement
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private void onRequest(Peer peer, SampleRequestMessage message) {
		SampleRequest request = new SampleRequest(message, peer);
		requestQueue.add(request);
	}

	private void processRequest(SampleRequest request) {
		Map<EUID, Set<LedgerIndex>> requestedIndicesByTag = request.getMessage().getRequestedIndicesByTag();
		Map<LedgerIndex, Set<AID>> aidsByIndex = gatherAidsByIndex(requestedIndicesByTag);
		Map<LedgerIndex, Map<AID, TemporalProof>> samplesByIndex = gatherSamplesByIndex(aidsByIndex);
		Map<EUID, Sample> collectedSamples = groupSamplesByTag(requestedIndicesByTag, samplesByIndex);
		SampleResponseMessage response = new SampleResponseMessage(collectedSamples);
		messageCentral.send(request.getPeer(), response);
	}

	private Map<EUID, Sample> groupSamplesByTag(Map<EUID, Set<LedgerIndex>> requestedIndicesByTag, Map<LedgerIndex, Map<AID, TemporalProof>> samplesByIndex) {
		Map<EUID, Sample> collectedSamples = new HashMap<>();
		for (EUID tag : requestedIndicesByTag.keySet()) {
			Set<LedgerIndex> requestedIndices = requestedIndicesByTag.get(tag);
			Map<LedgerIndex, Map<AID, TemporalProof>> temporalProofsByIndex = new HashMap<>();
			Set<LedgerIndex> unavailableIndices = new HashSet<>();

			for (LedgerIndex requestedIndex : requestedIndices) {
				Map<AID, TemporalProof> temporalProofs = samplesByIndex.get(requestedIndex);
				if (temporalProofs == null) {
					unavailableIndices.add(requestedIndex);
				} else {
					temporalProofsByIndex.put(requestedIndex, temporalProofs);
				}
			}
			collectedSamples.put(tag, new Sample(temporalProofsByIndex, unavailableIndices));
		}
		return collectedSamples;
	}

	private Map<LedgerIndex, Map<AID, TemporalProof>> gatherSamplesByIndex(Map<LedgerIndex, Set<AID>> aidsByIndex) {
		Map<LedgerIndex, Map<AID, TemporalProof>> samplesByIndex = new HashMap<>();
		for (LedgerIndex requestedIndex : aidsByIndex.keySet()) {
			Map<AID, TemporalProof> temporalProofsByAid = new HashMap<>();
			Set<AID> aids = aidsByIndex.get(requestedIndex);
			for (AID aid : aids) {
				TemporalProof localSample = sampleStore.getLocal(aid).orElseThrow(()
					-> new IllegalStateException("Internal cursor returned AID for which a local sample is unavailable"));
				temporalProofsByAid.put(aid, localSample);
			}
		}
		return samplesByIndex;
	}

	private Map<LedgerIndex, Set<AID>> gatherAidsByIndex(Map<EUID, Set<LedgerIndex>> requestedIndicesByTag) {
		Map<LedgerIndex, Set<AID>> aidsByIndex = new HashMap<>();

		for (EUID tag : requestedIndicesByTag.keySet()) {
			Set<LedgerIndex> requestedIndices = requestedIndicesByTag.get(tag);
			for (LedgerIndex requestedIndex : requestedIndices) {
				if (aidsByIndex.containsKey(requestedIndex)) {
					continue;
				}

				Set<AID> aids = new HashSet<>();
				LedgerCursor cursor = storeView.search(LedgerIndex.LedgerIndexType.UNIQUE, requestedIndex, LedgerSearchMode.EXACT);
				while (cursor != null) {
					aids.add(cursor.get());
					cursor = cursor.next();
				}
				aidsByIndex.put(requestedIndex, aids);
			}
		}
		return aidsByIndex;
	}

	private void onResponse(Peer peer, SampleResponseMessage message) {
		// TODO
		// remove tag and peer from pending sampling
	}

	/**
	 * Generates and atomically allocates a unique random tag that is currently unused
	 */
	private EUID allocateTag() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		EUID tag;
		do {
			byte[] value = new byte[EUID.BYTES];
			rng.nextBytes(value);
			tag = new EUID(value);
		} while (!samplingState.add(tag));
		return tag;
	}

	@Override
	public void close() {
		requestThreadPool.stop();
		messageCentral.removeListener(SampleRequestMessage.class, this::onRequest);
		messageCentral.removeListener(SampleResponseMessage.class, this::onResponse);
	}

	private static final class SampleRequest {
		private final SampleRequestMessage message;
		private final Peer peer;

		private SampleRequest(SampleRequestMessage message, Peer peer) {
			this.message = message;
			this.peer = peer;
		}

		private SampleRequestMessage getMessage() {
			return message;
		}

		private Peer getPeer() {
			return peer;
		}
	}
}
