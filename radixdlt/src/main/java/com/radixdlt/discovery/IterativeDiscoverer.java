/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.store.LedgerEntryStoreView;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.discovery.messages.IterativeDiscoveryRequestMessage;
import com.radixdlt.discovery.messages.IterativeDiscoveryResponseMessage;
import com.radixdlt.store.CursorStore;
import com.radixdlt.universe.Universe;
import org.radix.common.Syncronicity;
import org.radix.events.EventListener;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.AddressBookEvent;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeersAddedEvent;
import org.radix.network2.addressbook.PeersRemovedEvent;
import org.radix.network2.addressbook.PeersUpdatedEvent;
import org.radix.network2.messaging.MessageCentral;
import org.radix.utils.SimpleThreadPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Discoverer which uses logical clocks as a cursor to iterate through all relevant {@link AID}s of known nodes
 */
@Singleton
public final class IterativeDiscoverer implements AtomDiscoverer {
	private static final Logger log = Logging.getLogger("discoverer.iterative");

	private static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 5;
	private static final int DEFAULT_MAX_BACKOFF = 4;
	private static final int DEFAULT_RESPONSE_LIMIT = 10;
	private static final int DEFAULT_REQUEST_QUEUE_CAPACITY = 8192;
	private static final int DEFAULT_REQUEST_PROCESSOR_THREADS = 2;

	private final int maxBackoff;
	private final int responseLimit;
	private final int requestTimeoutSeconds;

	@VisibleForTesting
	final IterativeDiscoveryState discoveryState = new IterativeDiscoveryState();

	private final CursorStore cursorStore;
	private final LedgerEntryStoreView storeView;
	private final Scheduler scheduler;
	private final MessageCentral messageCentral;
	private final int universeMagic;

	private final Collection<AtomDiscoveryListener> discoveryListeners;

	private final BlockingQueue<IterativeDiscoveryRequest> requestQueue;
	private final SimpleThreadPool<IterativeDiscoveryRequest> requestThreadPool;

	@Inject
	public IterativeDiscoverer(
		LedgerEntryStoreView storeView,
		CursorStore cursorStore,
		Scheduler scheduler,
		MessageCentral messageCentral,
		Events events,
		IterativeDiscovererConfiguration configuration,
		Universe universe
	) {
		this.storeView = Objects.requireNonNull(storeView);
		this.cursorStore = Objects.requireNonNull(cursorStore);
		this.scheduler = Objects.requireNonNull(scheduler);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.universeMagic = Objects.requireNonNull(universe).getMagic();

		// TODO improve locking to something like in messaging
		this.discoveryListeners = Collections.synchronizedList(new ArrayList<>());

		// TODO replace with more restricted address book once it's hooked up
		// TODO remove listener when closed
		events.register(PeersAddedEvent.class, new LegacyPeersEventListenerAdapter(this::handleNewPeers));
		events.register(PeersUpdatedEvent.class, new LegacyPeersEventListenerAdapter(this::handleNewPeers));
		events.register(PeersRemovedEvent.class, (EventListener<PeersRemovedEvent>) event -> event.peers().stream()
			.filter(Peer::hasSystem)
			.forEach(IterativeDiscoverer.this::abandonDiscovery));

		this.responseLimit = configuration.responseLimit(DEFAULT_RESPONSE_LIMIT);
		this.maxBackoff = configuration.maxBackoff(DEFAULT_MAX_BACKOFF);
		this.requestTimeoutSeconds = configuration.requestTimeoutSeconds(DEFAULT_REQUEST_TIMEOUT_SECONDS);

		this.messageCentral.addListener(IterativeDiscoveryRequestMessage.class, this::onRequest);
		this.messageCentral.addListener(IterativeDiscoveryResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(configuration.requestProcessorThreads(DEFAULT_REQUEST_QUEUE_CAPACITY));
		int processorThreads = configuration.requestProcessorThreads(DEFAULT_REQUEST_PROCESSOR_THREADS);
		this.requestThreadPool = new SimpleThreadPool<>(
			"Iterative discovery processing",
			processorThreads,
			requestQueue::take,
			this::processRequest,
			log
		);
		this.requestThreadPool.start();
	}

	private void onRequest(Peer peer, IterativeDiscoveryRequestMessage message) {
		IterativeDiscoveryRequest request = new IterativeDiscoveryRequest(peer, message);
		requestQueue.add(request);
	}

	private void processRequest(IterativeDiscoveryRequest request) {
		IterativeDiscoveryResponseMessage response = fetchResponse(request.getMessage().getCursor());
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug("Responding to iterative discovery request from " + request.getPeer() + " with " + response.getCursor() + "");
		}
		messageCentral.send(request.getPeer(), response);
	}

	private void onResponse(Peer peer, IterativeDiscoveryResponseMessage message) {
		EUID peerNid = peer.getNID();
		notifyListeners(message.getAids(), peer);
		discoveryState.removeRequest(peerNid, message.getCursor().getLcPosition());

		boolean isLatest = updateCursor(peer, message.getCursor());
		if (discoveryState.contains(peerNid) && isLatest) {
			// if there is more to synchronise, request more immediately
			if (message.getCursor().hasNext()) {
				discoveryState.onDiscovering(peerNid);
				requestDiscovery(peer, message.getCursor().getNext());
			} else { // if synchronised, back off exponentially
				discoveryState.onDiscovered(peerNid);
				int timeout = 1 << Math.min(discoveryState.getBackoff(peerNid), maxBackoff);
				// TODO aggregate cancellables and cancel on stop
				scheduler.schedule(() -> initiateDiscovery(peer), timeout, TimeUnit.SECONDS);

				if (log.hasLevel(Logging.DEBUG)) {
					log.debug(String.format("Backing off from iterative discovery with %s for %d seconds as all synced up", peer, timeout));
				}
			}
		}
	}

	private void initiateDiscovery(Peer peer) {
		log.info("Initiating iterative discovery with " + peer);
		discoveryState.add(peer.getNID());

		long latestCursorPosition = getLatestCursorPosition(peer);
		LogicalClockCursor cursor = new LogicalClockCursor(latestCursorPosition);
		requestDiscovery(peer, cursor);
	}

	private void abandonDiscovery(Peer peer) {
		log.info("Abandoning iterative discovery with " + peer);
		discoveryState.remove(peer.getNID());
	}

	private void requestDiscovery(Peer peer, LogicalClockCursor cursor) {
		IterativeDiscoveryRequestMessage request = new IterativeDiscoveryRequestMessage(cursor, universeMagic);
		discoveryState.addRequest(peer.getNID(), cursor.getLcPosition());
		messageCentral.send(peer, request);
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug("Requesting iterative discovery from " + peer + " at " + cursor);
		}

		// re-request after a certain timeout if no response has been received
		scheduler.schedule(() -> {
			if (discoveryState.isPending(peer.getNID(), cursor.getLcPosition())) {
				if (log.hasLevel(Logging.DEBUG)) {
					log.debug("Iterative discovery request to peer " + peer + " at " + cursor + " has timed out, resending");
				}

				requestDiscovery(peer, cursor);
			}
		}, requestTimeoutSeconds, TimeUnit.SECONDS);
	}

	private long getLatestCursorPosition(Peer peer) {
		return this.cursorStore.get(peer.getNID()).orElse(0L);
	}

	private boolean updateCursor(Peer peer, LogicalClockCursor peerCursor) {
		EUID peerNid = peer.getNID();
		LogicalClockCursor nextCursor = peerCursor.hasNext() ? peerCursor.getNext() : peerCursor;
		long latestCursor = getLatestCursorPosition(peer);
		// store new cursor if higher than current
		if (nextCursor.getLcPosition() > latestCursor) {
			cursorStore.put(peerNid, nextCursor.getLcPosition());
		}
		// return whether this new cursor was the latest
		return nextCursor.getLcPosition() >= latestCursor;
	}

	private IterativeDiscoveryResponseMessage fetchResponse(LogicalClockCursor cursor) {
		long lcPosition = cursor.getLcPosition();
		ImmutableList<AID> aids = storeView.getNextCommitted(lcPosition, responseLimit);

		long nextLcPosition = lcPosition + aids.size();
		LogicalClockCursor nextCursor = null;
		// only set next cursor if the cursor was actually advanced
		if (nextLcPosition > lcPosition) {
			nextCursor = new LogicalClockCursor(nextLcPosition, null);
		}
		LogicalClockCursor responseCursor = new LogicalClockCursor(lcPosition, nextCursor);
		return new IterativeDiscoveryResponseMessage(aids, responseCursor, universeMagic);
	}

	@Override
	public void addListener(AtomDiscoveryListener listener) {
		discoveryListeners.add(listener);
	}

	@Override
	public void removeListener(AtomDiscoveryListener listener) {
		discoveryListeners.remove(listener);
	}

	private void notifyListeners(ImmutableList<AID> aids, Peer peer) {
		discoveryListeners.forEach(listener -> listener.accept(ImmutableSet.copyOf(aids), peer));
	}

	public void close() {
		requestThreadPool.stop();
		messageCentral.removeListener(IterativeDiscoveryRequestMessage.class, this::onRequest);
		messageCentral.removeListener(IterativeDiscoveryResponseMessage.class, this::onResponse);
	}

	private void handleNewPeers(Stream<Peer> peers) {
		peers
			.filter(Peer::hasSystem)
			.filter(peer -> !discoveryState.contains(peer.getNID()))
			.forEach(IterativeDiscoverer.this::initiateDiscovery);
	}

	// TODO remove once event listeners for address book are proper listeners
	private static final class LegacyPeersEventListenerAdapter implements EventListener<AddressBookEvent> {
		private final Consumer<Stream<Peer>> peersConsumer;

		private LegacyPeersEventListenerAdapter(Consumer<Stream<Peer>> peersConsumer) {
			this.peersConsumer = peersConsumer;
		}

		@Override
		public void process(AddressBookEvent event) throws Throwable {
			peersConsumer.accept(event.peers().stream());
		}

		@Override
		public Syncronicity getSyncronicity() {
			return Syncronicity.SYNCRONOUS;
		}
	}

	private static final class IterativeDiscoveryRequest {
		private final Peer peer;
		private final IterativeDiscoveryRequestMessage message;

		private IterativeDiscoveryRequest(Peer peer, IterativeDiscoveryRequestMessage message) {
			this.peer = peer;
			this.message = message;
		}

		private Peer getPeer() {
			return peer;
		}

		private IterativeDiscoveryRequestMessage getMessage() {
			return message;
		}
	}
}
