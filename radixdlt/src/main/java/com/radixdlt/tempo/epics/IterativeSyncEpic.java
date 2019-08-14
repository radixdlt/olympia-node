package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AbandonIterativeSyncAction;
import com.radixdlt.tempo.actions.OnCursorSynchronisedAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ResetAction;
import com.radixdlt.tempo.state.IterativeSyncState;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.IterativeCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.InitiateIterativeSyncAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeRequestAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeResponseAction;
import com.radixdlt.tempo.actions.TimeoutIterativeRequestAction;
import com.radixdlt.tempo.store.IterativeCursorStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeSyncEpic implements TempoEpic {
	private static final int ITERATIVE_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sync");
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds
	private static final int RESPONSE_AID_LIMIT = 128;

	private final AtomStoreView storeView;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	private final IterativeCursorStore latestCursorStore;

	private IterativeSyncEpic(AtomStoreView storeView, Supplier<ShardSpace> shardSpaceSupplier, IterativeCursorStore cursorStore) {
		this.storeView = storeView;
		this.shardSpaceSupplier = shardSpaceSupplier;
		this.latestCursorStore = cursorStore;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			PassivePeersState.class,
			IterativeSyncState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		PassivePeersState passivePeersState = bundle.get(PassivePeersState.class);
		IterativeSyncState syncState = bundle.get(IterativeSyncState.class);

		if (action instanceof ReselectPassivePeersAction) {
			// TODO is this an okay way of triggering this? could react to stage-changes instead..?
			// request iterative synchronisation with all new passive peers
			List<Peer> initiated = passivePeersState.peers()
				.filter(peer -> !syncState.contains(peer.getSystem().getNID()))
				.collect(Collectors.toList());
			// abandon iterative sync of no longer relevant passive peers
			List<EUID> abandoned = syncState.peers()
				.filter(nid -> !passivePeersState.contains(nid))
				.collect(Collectors.toList());
			if (!initiated.isEmpty() || !abandoned.isEmpty()) {
				logger.info("Discovered " + initiated.size() + " new passive peers to initiate sync with, abandoning " + abandoned.size() + " old passive peers");
			}
			return Stream.concat(
				initiated.stream().map(InitiateIterativeSyncAction::new),
				abandoned.stream().map(AbandonIterativeSyncAction::new)
			);
		} else if (action instanceof InitiateIterativeSyncAction) {
			Peer peer = ((InitiateIterativeSyncAction) action).getPeer();
			EUID peerNid = peer.getSystem().getNID();
			IterativeCursor lastCursor = this.latestCursorStore.get(peerNid).orElse(IterativeCursor.INITIAL);
			logger.info("Initiating iterative sync with " + peer);
			return Stream.of(new RequestIterativeSyncAction(peer, lastCursor, false));
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
			Peer peer = request.getPeer();
			long requestedLCPosition = request.getCursor().getLCPosition();
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

			// if no response, decide what to do after timeout
			if (syncState.isPending(peerNid, requestedLCPosition)) {
				// if we're still talking to that peer, just rerequest
				if (passivePeersState.contains(peerNid)) {
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Iterative request to %s at %s has timed out without response, resending", timeout.getPeer(), requestedLCPosition));
					}
					return Stream.of(new RequestIterativeSyncAction(timeout.getPeer(), timeout.getRequestedCursor(), false));
				} else { // otherwise report failed sync
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Iterative request to %s at %s has timed out without response, abandoning", timeout.getPeer(), requestedLCPosition));
					}
					return Stream.of(new AbandonIterativeSyncAction(peerNid));
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

			// update last known cursor if higher than current
			IterativeCursor nextCursor = peerCursor.hasNext() ? peerCursor.getNext() : peerCursor;
			long latestCursor = latestCursorStore.get(peerNid).map(IterativeCursor::getLCPosition).orElse(-1L);
			if (nextCursor.getLCPosition() > latestCursor) {
				latestCursorStore.put(peerNid, nextCursor);
			}
			boolean isLatest = nextCursor.getLCPosition() >= latestCursor;

			Stream<TempoAction> continuedActions = Stream.empty();
			// if the peer is still selected as a passive peer, continue
			if (passivePeersState.contains(peerNid)) {
				// if there is more to synchronise, request more immediately
				if (isLatest && peerCursor.hasNext()) {
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer, peerCursor.getNext(), true));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Continuing iterative sync with %s at %d",
							peer, peerCursor.getNext().getLCPosition()));
					}
				} else { // if synchronised, back off exponentially
					int timeout = 1 << syncState.getBackoff(peerNid);
					continuedActions = Stream.of(
						new OnCursorSynchronisedAction(peerNid),
						new InitiateIterativeSyncAction(peer).delay(timeout, TimeUnit.SECONDS)
					);
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
		} else if (action instanceof ResetAction) {
			latestCursorStore.reset();
		}

		return Stream.empty();
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
