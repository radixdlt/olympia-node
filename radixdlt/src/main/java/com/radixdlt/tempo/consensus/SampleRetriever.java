package com.radixdlt.tempo.consensus;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.consensus.messages.SampleRequestMessage;
import com.radixdlt.tempo.consensus.messages.SampleResponseMessage;
import com.radixdlt.tempo.store.SampleStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.time.TemporalProof;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class SampleRetriever implements Closeable {
	private static final Logger log = Logging.getLogger("SampleRetriever");
	private static final int REQUEST_QUEUE_CAPACITY = 8192;
	private static final int REQUEST_PROCESSOR_THREADS = 1;

	private final Scheduler scheduler;
	private final SampleStore sampleStore;
	private final MessageCentral messageCentral;

	private final SamplingState samplingState = new SamplingState();

	private final BlockingQueue<SampleRequest> requestQueue;
	private final SimpleThreadPool<SampleRequest> requestThreadPool;

	public SampleRetriever(Scheduler scheduler, SampleStore sampleStore, MessageCentral messageCentral) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.sampleStore = Objects.requireNonNull(sampleStore);
		this.messageCentral = Objects.requireNonNull(messageCentral);

		messageCentral.addListener(SampleRequestMessage.class, this::onRequest);
		messageCentral.addListener(SampleResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY);
		this.requestThreadPool = new SimpleThreadPool<>("Sample request processing", REQUEST_PROCESSOR_THREADS, requestQueue::take, this::processRequest, log);
		this.requestThreadPool.start();
	}

	public CompletableFuture<SamplingResult> sample(Collection<AID> aids, Collection<Peer> peers) {
		EUID tag = allocateTag();
		SampleRequestMessage request = new SampleRequestMessage(aids, tag);
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
		ImmutableSet.Builder<TemporalProof> collectedSamples = ImmutableSet.builder();
		ImmutableSet.Builder<AID> unavailableAids = ImmutableSet.builder();
		for (AID aid : request.getMessage().getAids()) {
			Optional<TemporalProof> localSample = sampleStore.getLocal(aid);
			if (localSample.isPresent()) {
				collectedSamples.add(localSample.get());
			} else {
				unavailableAids.add(aid);
			}
		}
		SampleResponseMessage response = new SampleResponseMessage(collectedSamples.build(), unavailableAids.build(), request.getMessage().getTag());
		messageCentral.send(request.getPeer(), response);
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
