package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.sync.IterativeCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.AcceptPassivePeersAction;
import com.radixdlt.tempo.sync.actions.InitiateIterativeSyncAction;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.RequestDeliveryAction;
import com.radixdlt.tempo.sync.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.sync.actions.SendIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.SendIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.TimeoutIterativeRequestAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeSyncEpic implements SyncEpic {
	private static final int ITERATIVE_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sync");
	private static final int MAX_BACKOFF = 5; // results in 2^5 -> 32 seconds
	private static final int RESPONSE_AID_LIMIT = 64;

	private final AtomStoreView storeView;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	// TODO lastCursor should be persisted
	private final Map<EUID, IterativeCursor> lastCursor;
	private final Map<EUID, Set<Long>> pendingCursors;
	private final Map<EUID, IterativeSyncState> syncState;
	private final Map<EUID, Integer> backoffCounter;

	private ImmutableSet<EUID> passivePeerNids;

	private IterativeSyncEpic(AtomStoreView storeView, Supplier<ShardSpace> shardSpaceSupplier) {
		this.storeView = storeView;
		this.shardSpaceSupplier = shardSpaceSupplier;
		this.lastCursor = new ConcurrentHashMap<>();
		this.pendingCursors = new ConcurrentHashMap<>();
		this.syncState = new ConcurrentHashMap<>();
		this.backoffCounter = new ConcurrentHashMap<>();
		this.passivePeerNids = ImmutableSet.of();
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof AcceptPassivePeersAction) {
			ImmutableSet<Peer> passivePeers = ((AcceptPassivePeersAction) action).getPassivePeers();
			this.passivePeerNids = passivePeers.stream()
				.map(peer -> peer.getSystem().getNID())
				.collect(ImmutableSet.toImmutableSet());

			// request iterative synchronisation with all new passive peers
			return passivePeers.stream()
				.filter(peer -> syncState.putIfAbsent(peer.getSystem().getNID(), IterativeSyncState.SYNCHRONISING) != IterativeSyncState.SYNCHRONISING)
				.map(InitiateIterativeSyncAction::new);
		} else if (action instanceof InitiateIterativeSyncAction) {
			Peer peer = ((InitiateIterativeSyncAction) action).getPeer();
			EUID peerNid = peer.getSystem().getNID();

			IterativeCursor lastCursor = this.lastCursor.getOrDefault(peerNid, IterativeCursor.INITIAL);
			long lastLCPosition = lastCursor.getLogicalClockPosition();

			// reset cursor if cursor is ahead of current peer logical clock
			if (lastLCPosition > peer.getSystem().getClock().get()) {
				lastCursor = IterativeCursor.INITIAL;
			}

			logger.info("Initiating iterative sync with " + peer);
			return Stream.of(new RequestIterativeSyncAction(peer, lastCursor));
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			Peer peer = request.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			long requestedLCPosition = request.getCursor().getLogicalClockPosition();

			// add requested lc position to pending cursors for that peer
			pendingCursors.compute(peerNid, (n, p) -> {
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
			return Stream.of(sendRequest, timeout.schedule(ITERATIVE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
		} else if (action instanceof TimeoutIterativeRequestAction) {
			// once the timeout has elapsed, check if we got a response
			TimeoutIterativeRequestAction timeout = (TimeoutIterativeRequestAction) action;
			EUID peerNid = timeout.getPeer().getSystem().getNID();
			long requestedLCPosition = timeout.getRequestedCursor().getLogicalClockPosition();

			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug(String.format("Iterative request to %s at %s has timed out", timeout.getPeer(), requestedLCPosition));
			}

			// if no response and we still want to talk to that peer, resend iterative request
			if (pendingCursors.get(peerNid).contains(requestedLCPosition) && passivePeerNids.contains(peerNid)) {
				return Stream.of(new RequestIterativeSyncAction(timeout.getPeer(), timeout.getRequestedCursor()));
			}
		} else if (action instanceof ReceiveIterativeRequestAction) {
			ReceiveIterativeRequestAction request = (ReceiveIterativeRequestAction) action;
			logger.info(String.format("Processing iterative request from %s for %d",
				request.getPeer(), request.getCursor().getLogicalClockPosition()));

			// retrieve and send back aids starting from the requested cursor up to the limit
			logger.info("storeView=" + storeView);
			logger.info("storeView.getClass=" + storeView.getClass());
			Pair<ImmutableList<AID>, IterativeCursor> aidsAndNext = storeView.getNext(request.getCursor(), RESPONSE_AID_LIMIT, request.getShardSpace());
			logger.info(String.format("Responding to iterative request from %s for %d with %d aids",
				request.getPeer(), request.getCursor().getLogicalClockPosition(), aidsAndNext.getFirst().size()));
			return Stream.of(new SendIterativeResponseAction(aidsAndNext.getFirst(), aidsAndNext.getSecond(), request.getPeer()));
		} else if (action instanceof ReceiveIterativeResponseAction) {
			ReceiveIterativeResponseAction response = (ReceiveIterativeResponseAction) action;
			Peer peer = response.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			IterativeCursor peerCursor = response.getCursor();

			// update last known cursor
			lastCursor.put(peerNid, peerCursor);
			// remove requested position from pending cursors
			long requestedLCPosition = peerCursor.getLogicalClockPosition();
			pendingCursors.computeIfPresent(peerNid, (n, p) -> {
				p.remove(requestedLCPosition);
				return p;
			});

			Stream<SyncAction> continuedActions = Stream.empty();
			// if the peer is still selected as a passive peer, continue synchronisation
			if (passivePeerNids.contains(peerNid)) {
				// if there is more to synchronise, request more immediately
				if (peerCursor.hasNext()) {
					syncState.put(peerNid, IterativeSyncState.SYNCHRONISING);
					backoffCounter.put(peerNid, 0);
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer, peerCursor.getNext()));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Received iterative response from %s, continuing iterative sync at %d",
							peer, peerCursor.getNext().getLogicalClockPosition()));
					}
				} else { // if synchronised, back off exponentially
					syncState.put(peerNid, IterativeSyncState.SYNCHRONISED);
					int backoff = backoffCounter.compute(peerNid, (n, c) -> c == null ? 1 : Math.max(MAX_BACKOFF, c + 1));
					int timeout = 1 << backoff;
					continuedActions = Stream.of(new InitiateIterativeSyncAction(peer).schedule(timeout, TimeUnit.SECONDS));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Received iterative response from %s, backing off as all synced up", peer));
					}
				}
			}

			return Stream.concat(
				continuedActions,
				Stream.of(new RequestDeliveryAction(response.getAids(), response.getPeer()))
			);
		}

		return Stream.empty();
	}

	enum IterativeSyncState {
		SYNCHRONISING,
		SYNCHRONISED
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Supplier<ShardSpace> shardSpaceSupplier;
		private AtomStoreView storeView;

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

		public IterativeSyncEpic build() {
			Objects.requireNonNull(storeView, "storeView is required");
			Objects.requireNonNull(shardSpaceSupplier, "shardSpaceSupplier is required");

			return new IterativeSyncEpic(storeView, shardSpaceSupplier);
		}
	}
}
