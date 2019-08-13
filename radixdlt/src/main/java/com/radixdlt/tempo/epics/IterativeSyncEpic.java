package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.InitiateIterativeSyncAction;
import com.radixdlt.tempo.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.SendIterativeRequestAction;
import com.radixdlt.tempo.actions.SendIterativeResponseAction;
import com.radixdlt.tempo.actions.TimeoutIterativeRequestAction;
import com.radixdlt.tempo.store.IterativeCursorStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class IterativeSyncEpic implements TempoEpic {
	private static final int ITERATIVE_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sync");
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds
	private static final int RESPONSE_AID_LIMIT = 128;

	private final AtomStoreView storeView;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	private final IterativeCursorStore latestCursors;
	private final Map<EUID, Set<Long>> pendingRequests;
	private final Map<EUID, IterativeSyncStage> syncState;
	private final Map<EUID, Integer> backoffCounter;

	private IterativeSyncEpic(AtomStoreView storeView, Supplier<ShardSpace> shardSpaceSupplier, IterativeCursorStore cursorStore) {
		this.storeView = storeView;
		this.shardSpaceSupplier = shardSpaceSupplier;
		this.latestCursors = cursorStore;

		this.pendingRequests = new ConcurrentHashMap<>();
		this.syncState = new ConcurrentHashMap<>();
		this.backoffCounter = new ConcurrentHashMap<>();
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(PassivePeersState.class);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ReselectPassivePeersAction) {
			// TODO not very clean way of triggering this, could react to stage-changes?
			PassivePeersState passivePeers = bundle.get(PassivePeersState.class);

			// request iterative synchronisation with all new passive peers
			return passivePeers.getSelectedPeers().values().stream()
				.filter(peer -> syncState.putIfAbsent(peer.getSystem().getNID(), IterativeSyncStage.SYNCHRONISING) != IterativeSyncStage.SYNCHRONISING)
				.map(InitiateIterativeSyncAction::new);
		} else if (action instanceof InitiateIterativeSyncAction) {
			Peer peer = ((InitiateIterativeSyncAction) action).getPeer();
			EUID peerNid = peer.getSystem().getNID();

			IterativeCursor lastCursor = this.latestCursors.get(peerNid).orElse(IterativeCursor.INITIAL);
			logger.info("Initiating iterative sync with " + peer);
			return Stream.of(new RequestIterativeSyncAction(peer, lastCursor));
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			Peer peer = request.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			long requestedLCPosition = request.getCursor().getLCPosition();

			// add requested lc position to pending cursors for that peer
			pendingRequests.compute(peerNid, (n, p) -> {
				if (p == null) {
					p = new HashSet<>();
				}
				p.add(requestedLCPosition);
				return p;
			});

			logger.info("Requesting iterative sync from " + peer + " starting at " + requestedLCPosition);
			// send iterative request for aids starting with last cursor
			ShardSpace shardRange = shardSpaceSupplier.get();
			SendIterativeRequestAction sendRequest = new SendIterativeRequestAction(shardRange, request.getCursor(), peer);
			// schedule timeout after which response will be checked
			TimeoutIterativeRequestAction timeout = new TimeoutIterativeRequestAction(peer, request.getCursor());
			return Stream.of(sendRequest, timeout.delay(ITERATIVE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
		} else if (action instanceof TimeoutIterativeRequestAction) {
			// once the timeout has elapsed, check if we got a response
			TimeoutIterativeRequestAction timeout = (TimeoutIterativeRequestAction) action;
			EUID peerNid = timeout.getPeer().getSystem().getNID();
			long requestedLCPosition = timeout.getRequestedCursor().getLCPosition();

			PassivePeersState passivePeers = bundle.get(PassivePeersState.class);
			// if no response, decide what to do after timeout
			if (pendingRequests.get(peerNid).contains(requestedLCPosition)) {
				// if we're still talking to that peer, just rerequest
				if (passivePeers.contains(peerNid)) {
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Iterative request to %s at %s has timed out without response, resending", timeout.getPeer(), requestedLCPosition));
					}
					return Stream.of(new RequestIterativeSyncAction(timeout.getPeer(), timeout.getRequestedCursor()));
				} else { // otherwise do nothing
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Iterative request to %s at %s has timed out without response", timeout.getPeer(), requestedLCPosition));
					}
					return Stream.empty();
				}
			}
		} else if (action instanceof ReceiveIterativeRequestAction) {
			ReceiveIterativeRequestAction request = (ReceiveIterativeRequestAction) action;

			// retrieve and send back aids starting from the requested cursor up to the limit
			Pair<ImmutableList<AID>, IterativeCursor> aidsAndCursor = storeView.getNext(request.getCursor(), RESPONSE_AID_LIMIT, request.getShardSpace());
			IterativeCursor cursor = aidsAndCursor.getSecond();
			ImmutableList<AID> aids = aidsAndCursor.getFirst();
			logger.info(String.format("Responding to iterative request from %s for %d with %d aids (next=%s)",
				request.getPeer(), request.getCursor().getLCPosition(), aids.size(),
				cursor.hasNext() ? cursor.getNext().getLCPosition() : "<none>"));
			return Stream.of(new SendIterativeResponseAction(aids, cursor, request.getPeer()));
		} else if (action instanceof ReceiveIterativeResponseAction) {
			ReceiveIterativeResponseAction response = (ReceiveIterativeResponseAction) action;
			Peer peer = response.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			IterativeCursor peerCursor = response.getCursor();

			logger.info(String.format("Received iterative response from %s with %s aids", peer, response.getAids().size()));
			// remove requested position from pending cursors
			long requestedLCPosition = peerCursor.getLCPosition();
			pendingRequests.computeIfPresent(peerNid, (n, p) -> {
				p.remove(requestedLCPosition);
				return p;
			});

			// update last known cursor if higher than current
			IterativeCursor nextCursor = peerCursor.hasNext() ? peerCursor.getNext() : peerCursor;
			long latestCursor = latestCursors.get(peerNid).map(IterativeCursor::getLCPosition).orElse(-1L);
			if (nextCursor.getLCPosition() > latestCursor) {
				latestCursors.put(peerNid, nextCursor);
			}
			boolean isLatest = nextCursor.getLCPosition() >= latestCursor;

			PassivePeersState passivePeers = bundle.get(PassivePeersState.class);
			Stream<TempoAction> continuedActions = Stream.empty();
			// if the peer is still selected as a passive peer, continue
			if (passivePeers.contains(peerNid)) {
				// if there is more to synchronise, request more immediately
				if (isLatest && peerCursor.hasNext()) {
					syncState.put(peerNid, IterativeSyncStage.SYNCHRONISING);
					backoffCounter.put(peerNid, 0);
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer, peerCursor.getNext()));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Continuing iterative sync with %s at %d",
							peer, peerCursor.getNext().getLCPosition()));
					}
				} else { // if synchronised, back off exponentially
					syncState.put(peerNid, IterativeSyncStage.SYNCHRONISED);
					int backoff = backoffCounter.compute(peerNid, (n, c) -> c == null ? 0 : Math.min(MAX_BACKOFF, c + 1));
					int timeout = 1 << backoff;
					continuedActions = Stream.of(new InitiateIterativeSyncAction(peer).delay(timeout, TimeUnit.SECONDS));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Backing off from iterative sync with %s for %d seconds as all synced up", peer, timeout));
					}
				}
			}

			Stream<TempoAction> deliveryActions = Stream.empty();
			// if any aids were returned, request delivery
			if (!response.getAids().isEmpty()) {
				deliveryActions = Stream.of(new RequestDeliveryAction(response.getAids(), response.getPeer()));
			}
			return Stream.concat(continuedActions, deliveryActions);
		}

		return Stream.empty();
	}

	public enum IterativeSyncStage {
		SYNCHRONISING,
		SYNCHRONISED
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Supplier<ShardSpace> shardSpaceSupplier;
		private AtomStoreView storeView;
		private IterativeCursorStore cursorStore;

		private Builder() {
		}

		public Builder storeView(AtomStoreView storeView) {
			this.storeView = storeView;
			return this;
		}

		public Builder shardSpaceSupplier(Supplier<ShardSpace> shardSpaceSupplier) {
			this.shardSpaceSupplier = shardSpaceSupplier;
			return this;
		}

		public Builder cursorStore(IterativeCursorStore cursorStore) {
			this.cursorStore = cursorStore;
			return this;
		}

		public IterativeSyncEpic build() {
			Objects.requireNonNull(storeView, "storeView is required");
			Objects.requireNonNull(shardSpaceSupplier, "shardSpaceSupplier is required");
			Objects.requireNonNull(cursorStore, "cursorStore is required");

			return new IterativeSyncEpic(storeView, shardSpaceSupplier, cursorStore);
		}
	}
}
