package com.radixdlt.tempo.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.LegacyAddressBook;
import com.radixdlt.tempo.LegacyAddressBookListener;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.discovery.messages.IterativeDiscoveryRequestMessage;
import com.radixdlt.tempo.discovery.messages.IterativeDiscoveryResponseMessage;
import com.radixdlt.tempo.store.berkeley.BerkeleyLCCursorStore;
import com.radixdlt.tempo.store.CommitmentStore;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public final class IterativeDiscoverer implements Closeable, AtomDiscoverer {
	private static final Logger log = Logging.getLogger("IterativeDiscoverer");

	private static final int REQUEST_TIMEOUT_SECONDS = 5;
	// maximum backoff when synchronised (exponential, e.g. 2^4 = 16 seconds)
	private static final int MAX_BACKOFF = 4;
	// how many commitments to send per response (32 bytes for commitment + 8 bytes for position)
	private static final int RESPONSE_LIMIT = 10;
	private static final int REQUEST_QUEUE_CAPACITY = 8192;
	private static final int REQUEST_PROCESSOR_THREADS = 2;

	private final EUID self;

	@VisibleForTesting
	final BerkeleyLCCursorStore cursorStore;

	@VisibleForTesting
	final IterativeDiscoveryState discoveryState = new IterativeDiscoveryState();

	private final CommitmentStore commitmentStore;
	private final AtomStoreView storeView;
	private final Scheduler scheduler;
	private final MessageCentral messageCentral;
	private final LegacyAddressBook selectedPeers;

	private final Collection<AtomDiscoveryListener> discoveryListeners;

	private final BlockingQueue<IterativeDiscoveryRequest> requestQueue;
	private final SimpleThreadPool<IterativeDiscoveryRequest> requestThreadPool;

	public IterativeDiscoverer(
		EUID self,
		AtomStoreView storeView,
		CommitmentStore commitmentStore,
		DatabaseEnvironment dbEnv,
		Scheduler scheduler,
		MessageCentral messageCentral,
		LegacyAddressBook selectedPeers
	) {
		this.self = Objects.requireNonNull(self);
		this.storeView = Objects.requireNonNull(storeView);
		this.commitmentStore = Objects.requireNonNull(commitmentStore);
		this.scheduler = Objects.requireNonNull(scheduler);
		this.messageCentral = Objects.requireNonNull(messageCentral);
		this.selectedPeers = Objects.requireNonNull(selectedPeers);

		// TODO improve locking to something like in messaging
		this.discoveryListeners = Collections.synchronizedList(new ArrayList<>());
		this.cursorStore = new BerkeleyLCCursorStore(dbEnv);
		this.cursorStore.open();

		// TODO replace with regular address book once it's hooked up
		// TODO remove listener when closed
		this.selectedPeers.addListener(new LegacyAddressBookListener() {
			@Override
			public void onPeerAdded(Set<Peer> peers) {
				peers.stream()
					.filter(peer -> !discoveryState.contains(peer.getSystem().getNID()))
					.forEach(IterativeDiscoverer.this::initiateDiscovery);
			}

			@Override
			public void onPeerRemoved(Set<Peer> peers) {
				peers.forEach(IterativeDiscoverer.this::abandonDiscovery);
			}
		});

		this.messageCentral.addListener(IterativeDiscoveryRequestMessage.class, this::onRequest);
		this.messageCentral.addListener(IterativeDiscoveryResponseMessage.class, this::onResponse);

		this.requestQueue = new ArrayBlockingQueue<>(REQUEST_QUEUE_CAPACITY);
		this.requestThreadPool = new SimpleThreadPool<>("Iterative discovery processing", REQUEST_PROCESSOR_THREADS, requestQueue::take, this::processRequest, log);
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
		EUID peerNid = peer.getSystem().getNID();
		notifyListeners(message.getAids(), peer);
		commitmentStore.put(peerNid, message.getCommitments(), message.getCursor().getLcPosition());
		discoveryState.removeRequest(peerNid, message.getCursor().getLcPosition());

		boolean isLatest = updateCursor(peer, message.getCursor());
		if (selectedPeers.contains(peerNid) && isLatest) {
			// if there is more to synchronise, request more immediately
			if (message.getCursor().hasNext()) {
				discoveryState.onDiscovering(peerNid);
				requestDiscovery(peer, message.getCursor().getNext());
			} else { // if synchronised, back off exponentially
				discoveryState.onDiscovered(peerNid);
				int timeout = 1 << Math.min(discoveryState.getBackoff(peerNid), MAX_BACKOFF);
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
		discoveryState.add(peer.getSystem().getNID());

		long latestCursorPosition = getLatestCursorPosition(peer);
		LogicalClockCursor cursor = new LogicalClockCursor(latestCursorPosition);
		requestDiscovery(peer, cursor);
	}

	private void abandonDiscovery(Peer peer) {
		log.info("Abandoning iterative discovery with " + peer);
		discoveryState.remove(peer.getSystem().getNID());
	}

	private void requestDiscovery(Peer peer, LogicalClockCursor cursor) {
		IterativeDiscoveryRequestMessage request = new IterativeDiscoveryRequestMessage(cursor);
		discoveryState.addRequest(peer.getSystem().getNID(), cursor.getLcPosition());
		messageCentral.send(peer, request);
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug("Requesting iterative discovery from " + peer + " at " + cursor);
		}

		// re-request after a certain timeout if no response has been received
		scheduler.schedule(() -> {
			if (discoveryState.isPending(peer.getSystem().getNID(), cursor.getLcPosition())) {
				if (log.hasLevel(Logging.DEBUG)) {
					log.debug("Iterative discovery request to peer " + peer + " at " + cursor + " has timed out, resending");
				}

				requestDiscovery(peer, cursor);
			}
		}, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private long getLatestCursorPosition(Peer peer) {
		return this.cursorStore.get(peer.getSystem().getNID()).orElse(0L);
	}

	private boolean updateCursor(Peer peer, LogicalClockCursor peerCursor) {
		EUID peerNid = peer.getSystem().getNID();
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
		ImmutableList<Hash> commitments = commitmentStore.getNext(self, lcPosition, RESPONSE_LIMIT);
		ImmutableList<AID> aids = storeView.getNext(lcPosition, RESPONSE_LIMIT);

		// there should be at least as many commitments as aids, otherwise the stores are corrupt
		if (commitments.size() < aids.size()) {
			throw new IllegalStateException(String.format("Missing commitments at [%d, %d[",
				lcPosition, lcPosition + aids.size()));
		}

		long nextLcPosition = lcPosition + commitments.size();
		LogicalClockCursor nextCursor = null;
		// only set next cursor if the cursor was actually advanced
		if (nextLcPosition > lcPosition) {
			nextCursor = new LogicalClockCursor(nextLcPosition, null);
		}
		LogicalClockCursor responseCursor = new LogicalClockCursor(lcPosition, nextCursor);
		return new IterativeDiscoveryResponseMessage(commitments, aids, responseCursor);
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
		discoveryListeners.forEach(listener -> listener.accept(aids, peer));
	}

	public void reset() {
		cursorStore.reset();
		commitmentStore.reset();
	}

	@Override
	public void close() {
		cursorStore.close();
		commitmentStore.close();

		messageCentral.removeListener(IterativeDiscoveryRequestMessage.class, this::onRequest);
		messageCentral.removeListener(IterativeDiscoveryResponseMessage.class, this::onResponse);
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
