package com.radixdlt.tempo.delivery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.Resource;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.delivery.messages.DeliveryRequestMessage;
import com.radixdlt.tempo.delivery.messages.DeliveryResponseMessage;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Singleton
public final class LazyRequestDeliverer implements Resource, AtomDeliverer, RequestDeliverer {
	private static final Logger log = Logging.getLogger("deliverer.request");

	private static final int DEFAULT_REQUEST_QUEUE_CAPACITY = 8192;
	private static final int DEFAULT_REQUEST_PROCESSOR_THREADS = 2;
	private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;

	private final int requestTimeoutSeconds;

	@VisibleForTesting
	final RequestDeliveryState deliveryState = new RequestDeliveryState();

	private final Scheduler scheduler;
	private final MessageCentral messageCentral;
	private final TempoAtomStoreView storeView;
	private final Collection<AtomDeliveryListener> deliveryListeners;

	private final BlockingQueue<AtomDeliveryRequest> requestQueue;
	private final SimpleThreadPool<AtomDeliveryRequest> requestThreadPool;

	@Inject
	public LazyRequestDeliverer(
		Scheduler scheduler,
		MessageCentral messageCentral,
		TempoAtomStoreView storeView,
		LazyRequestDelivererConfiguration configuration
	) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.storeView = Objects.requireNonNull(storeView);

		// TODO improve locking to something like in messaging
		this.deliveryListeners = Collections.synchronizedList(new ArrayList<>());

		this.requestTimeoutSeconds = configuration.requestTimeoutSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS);

		this.messageCentral.addListener(DeliveryRequestMessage.class, this::onRequest);
		this.messageCentral.addListener(DeliveryResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(configuration.requestQueueCapacity(DEFAULT_REQUEST_QUEUE_CAPACITY));
		int processorThreads = configuration.requestProcessorThreads(DEFAULT_REQUEST_PROCESSOR_THREADS);
		this.requestThreadPool = new SimpleThreadPool<>("Atom delivery processing", processorThreads, requestQueue::take, this::processRequest, log);
		this.requestThreadPool.start();
	}

	private void onRequest(Peer peer, DeliveryRequestMessage message) {
		AtomDeliveryRequest request = new AtomDeliveryRequest(peer, message);
		requestQueue.add(request);
	}

	private void processRequest(AtomDeliveryRequest request) {
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug(String.format("Processing atom delivery request for %d aids from %s",
				request.getMessage().getAids().size(), request.getPeer()));
		}

		request.getMessage().getAids().stream()
			.map(storeView::get)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.map(DeliveryResponseMessage::new)
			.forEach(response -> messageCentral.send(request.getPeer(), response));
	}

	private void onResponse(Peer peer, DeliveryResponseMessage message) {
		TempoAtom atom = message.getAtom();
		deliveryState.removeRequest(atom.getAID());
		notifyListeners(peer, atom);
	}

	@Override
	public void tryDeliver(Collection<AID> aids, Peer peer) {
		// early out if there is nothing to do
		if (aids.isEmpty()) {
			return;
		}

		ImmutableList<AID> missingAids = aids.stream()
			.filter(aid -> !storeView.contains(aid))
			.collect(ImmutableList.toImmutableList());
		// if we already have all requested aids, just bail
		if (missingAids.isEmpty()) {
			return;
		}

		ImmutableList.Builder<AID> unrequestedAids = ImmutableList.builder();
		for (AID aid : missingAids) {
			if (deliveryState.isPending(aid)) {
				deliveryState.addFallback(aid, peer);
			} else {
				unrequestedAids.add(aid);
			}
		}
		requestDelivery(unrequestedAids.build(), peer);
	}

	private void requestDelivery(Collection<AID> aids, Peer peer) {
		// early out if there is nothing to do
		if (aids.isEmpty()) {
			return;
		}

		if (log.hasLevel(Logging.DEBUG)) {
			log.debug("Requesting delivery of " + aids.size() + " aids from " + peer);
		}
		DeliveryRequestMessage request = new DeliveryRequestMessage(aids);
		deliveryState.addRequest(aids, peer.getNID());
		messageCentral.send(peer, request);

		// TODO aggregate cancellables and cancel on stop
		scheduler.schedule(() -> {
			ImmutableList<AID> missingAids = aids.stream()
				.filter(deliveryState::isPending)
				.collect(ImmutableList.toImmutableList());
			if (!missingAids.isEmpty()) {
				handleFailedDelivery(missingAids, peer);
				// TODO retry
			}
		}, requestTimeoutSeconds, TimeUnit.SECONDS);
	}

	private void handleFailedDelivery(Collection<AID> missingAids, Peer peer) {
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug("Delivery of " + missingAids.size() + " aids from peer " + peer + " failed, attempting retry");
		}

		// get fallback peers and aggregate all aids that can be requested from a peer
		Map<EUID, Set<AID>> retriesByNid = new HashMap<>();
		Map<EUID, Peer> peersByNid = new HashMap<>();
		List<AID> undeliverableAids = new ArrayList<>();
		for (AID missingAid : missingAids) {
			Optional<Peer> fallback = deliveryState.getFallback(missingAid);
			if (fallback.isPresent()) {
				Peer fallbackPeer = fallback.get();
				EUID fallbackPeerNid = fallbackPeer.getNID();
				peersByNid.putIfAbsent(fallbackPeerNid, fallbackPeer);
				retriesByNid.computeIfAbsent(fallbackPeerNid, x -> new HashSet<>()).add(missingAid);
			} else {
				undeliverableAids.add(missingAid);
			}
		}

		retriesByNid.forEach((nid, aids) -> requestDelivery(aids, peersByNid.get(nid)));
		if (!undeliverableAids.isEmpty()) {
			log.warn("Delivery of " + undeliverableAids.size() + " is currently impossible, no peers available");
		}
	}

	@Override
	public void addListener(AtomDeliveryListener listener) {
		deliveryListeners.add(listener);
	}

	@Override
	public void removeListener(AtomDeliveryListener listener) {
		deliveryListeners.remove(listener);
	}

	private void notifyListeners(Peer peer, TempoAtom atom) {
		deliveryListeners.forEach(listener -> listener.accept(atom, peer));
	}

	@Override
	public void reset() {
		deliveryState.reset();
	}

	@Override
	public void close() {
		requestThreadPool.stop();
	}

	private static final class AtomDeliveryRequest {
		private final Peer peer;
		private final DeliveryRequestMessage message;

		private AtomDeliveryRequest(Peer peer, DeliveryRequestMessage message) {
			this.peer = peer;
			this.message = message;
		}

		private Peer getPeer() {
			return peer;
		}

		public DeliveryRequestMessage getMessage() {
			return message;
		}
	}
}
