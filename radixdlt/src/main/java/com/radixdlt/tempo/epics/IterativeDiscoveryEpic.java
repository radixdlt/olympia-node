package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.reactive.TempoFlowSource;
import com.radixdlt.tempo.reactive.TempoFlow;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ResetAction;
import com.radixdlt.tempo.actions.TimeoutCursorDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.state.IterativeDiscoveryState;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.LogicalClockCursorStore;
import com.radixdlt.tempo.store.LogicalClockCursorStore.CursorType;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalVertex;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IterativeDiscoveryEpic implements TempoEpic {
	// timeout for cursor requests
	private static final int CURSOR_TIMEOUT_SECONDS = 5;
	// timeout for position requests
	private static final int POSITION_TIMEOUT_SECONDS = 5;
	// maximum backoff when synchronised (exponential, e.g. 2^4 = 16 seconds)
	private static final int MAX_BACKOFF = 4;
	// how many commitments to send per response (32 bytes for commitment + 8 bytes for position)
	private static final int RESPONSE_LIMIT = 10;

	private static final Logger logger = Logging.getLogger("Sync");

	private final EUID self;
	private final AtomStoreView storeView;
	private final Supplier<ShardSpace> shardSpaceSupplier;

	private final LogicalClockCursorStore latestCursorStore;
	private final CommitmentStore commitmentStore;

	private IterativeDiscoveryEpic(EUID self, AtomStoreView storeView, Supplier<ShardSpace> shardSpaceSupplier, LogicalClockCursorStore cursorStore, CommitmentStore commitmentStore) {
		this.self = self;
		this.storeView = storeView;
		this.shardSpaceSupplier = shardSpaceSupplier;
		this.latestCursorStore = cursorStore;
		this.commitmentStore = commitmentStore;
	}

	@Override
	public TempoFlow<TempoAction> epic(TempoFlowSource flow) {
		flow.of(AcceptAtomAction.class)
			.map(AcceptAtomAction::getAtom)
			.forEach(atom -> {
					TemporalVertex ownVertex = atom.getTemporalProof().getVertexByNID(self);
					if (ownVertex == null) {
						throw new TempoException("Accepted atom '" + atom.getAID() + "' has no vertex by self");
					} else {
						commitmentStore.put(self, ownVertex.getClock(), ownVertex.getCommitment());
					}
				}
			);

		// TODO flowify
		TempoFlow<TempoAction> reselectPeers = flow.of(ReselectPassivePeersAction.class)
			.flatMapStateful((request, state) -> {
				IterativeDiscoveryState cursorDiscovery = state.get(IterativeDiscoveryState.class);
				PassivePeersState passivePeers = state.get(PassivePeersState.class);
				// TODO is this an okay way of triggering this? could react to stage-changes instead..?
				// initiate iterative discovery with all new passive peers
				List<Peer> initiated = passivePeers.peers()
					.filter(peer -> !cursorDiscovery.contains(peer.getSystem().getNID()))
					.collect(Collectors.toList());
				// abandon iterative discovery of no longer relevant passive peers
				List<EUID> abandoned = cursorDiscovery.peers()
					.filter(nid -> !passivePeers.contains(nid))
					.collect(Collectors.toList());
				if (!initiated.isEmpty() || !abandoned.isEmpty()) {
					logger.info(String.format(
						"Discovered %d new passive peers to initiate sync with, abandoning %d old passive peers",
						initiated.size(), abandoned.size()));
				}
				return Stream.concat(
					initiated.stream().map(InitiateIterativeDiscoveryAction::new),
					abandoned.stream().map(AbandonIterativeDiscoveryAction::new)
				);
			}, IterativeDiscoveryState.class, PassivePeersState.class);

		TempoFlow<TempoAction> initiateDiscovery = flow.of(InitiateIterativeDiscoveryAction.class)
			.map(initiate -> {
				Peer peer = initiate.getPeer();
				EUID peerNid = peer.getSystem().getNID();
				long lastCursor = this.latestCursorStore.get(peerNid, CursorType.DISCOVERY).orElse(0L);
				logger.info("Initiating iterative discovery with '" + peer + "' from '" + lastCursor + "'");
				return new RequestIterativeSyncAction(peer, new LogicalClockCursor(lastCursor), false);
			});

		// TODO flowify
		// TODO change iterative "sync" to proper name
		TempoFlow<TempoAction> requestIterativeSync = flow.of(RequestIterativeSyncAction.class)
			.flatMap(request -> {
				Peer peer = request.getPeer();
				long requestedLCPosition = request.getCursor().getLcPosition();
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Requesting iterative discovery from '%s' starting at '%d'",
						peer, requestedLCPosition));
				}
				// send iterative request for aids starting with last cursor
				ShardSpace shardRange = shardSpaceSupplier.get();
				SendIterativeDiscoveryRequestAction sendRequest = new SendIterativeDiscoveryRequestAction(shardRange, request.getCursor(), peer);
				// schedule timeout after which response will be checked
				TimeoutCursorDiscoveryRequestAction timeout = new TimeoutCursorDiscoveryRequestAction(request.getCursor(), peer);
				return Stream.of(sendRequest, timeout.delay(CURSOR_TIMEOUT_SECONDS, TimeUnit.SECONDS));
			});

		// TODO flowify!
		TempoFlow<TempoAction> timeoutCursorRequests = flow.of(TimeoutCursorDiscoveryRequestAction.class)
			.flatMapStateful((timeout, state) -> {
				IterativeDiscoveryState cursorDiscovery = state.get(IterativeDiscoveryState.class);
				PassivePeersState passivePeers = state.get(PassivePeersState.class);

				// once the timeout has elapsed, check if we got a response
				EUID peerNid = timeout.getPeer().getSystem().getNID();
				long requestedLCPosition = timeout.getRequestedCursor().getLcPosition();

				// if no response, decide what to do after timeout
				if (cursorDiscovery.isPending(peerNid, requestedLCPosition)) {
					// if we're still talking to that peer, just re-request
					if (passivePeers.contains(peerNid)) {
						if (logger.hasLevel(Logging.DEBUG)) {
							logger.debug(String.format("Iterative request to %s at %s has timed out without response, resending", timeout.getPeer(), requestedLCPosition));
						}
						return Stream.of(new RequestIterativeSyncAction(timeout.getPeer(), timeout.getRequestedCursor(), false));
					} else { // otherwise report failed sync
						if (logger.hasLevel(Logging.DEBUG)) {
							logger.debug(String.format("Iterative request to %s at %s has timed out without response, abandoning", timeout.getPeer(), requestedLCPosition));
						}
						return Stream.of(new AbandonIterativeDiscoveryAction(peerNid));
					}
				}
				return Stream.empty();
			}, IterativeDiscoveryState.class, PassivePeersState.class);

		// TODO flowify
		// retrieve and send back commitments starting from the requested cursor up to the limit
		TempoFlow<TempoAction> receiveCursorRequests = flow.of(ReceiveIterativeDiscoveryRequestAction.class)
			.map(request -> {
				long lcPosition = request.getCursor().getLcPosition();
				// TODO Commitments may be larger than aids as aid may have been deleted but commitments remain.
				// TODO This does not cause any immediate issues but should be addressed in the long run.
				ImmutableList<Hash> commitments = commitmentStore.getNext(self, lcPosition, RESPONSE_LIMIT);
				long nextLcPosition = lcPosition + commitments.size();
				ImmutableList<AID> aids = storeView.getNext(lcPosition, RESPONSE_LIMIT);

				LogicalClockCursor nextCursor = null;
				// only set next cursor if the cursor was actually advanced
				if (nextLcPosition > lcPosition) {
					nextCursor = new LogicalClockCursor(nextLcPosition, null);
				}
				LogicalClockCursor responseCursor = new LogicalClockCursor(lcPosition, nextCursor);
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Responding to iterative discovery request from %s for %d with %d items (next=%s)",
						request.getPeer(), lcPosition, commitments.size(), responseCursor.hasNext() ? nextLcPosition : "<none>"));
				}
				return new SendIterativeDiscoveryResponseAction(commitments, aids, responseCursor, request.getPeer());
			});

		// store received commitments
		flow.of(ReceiveIterativeDiscoveryResponseAction.class)
			.forEach(response -> {
				EUID peerNid = response.getPeer().getSystem().getNID();
				LogicalClockCursor peerCursor = response.getCursor();
				commitmentStore.put(peerNid, response.getCommitments(), peerCursor.getLcPosition());
			});

		// TODO flowify, breakup!
		TempoFlow<TempoAction> receiveCursorResponses = flow.of(ReceiveIterativeDiscoveryResponseAction.class)
			.flatMapStateful((response, state) -> {
				IterativeDiscoveryState cursorDiscovery = state.get(IterativeDiscoveryState.class);
				PassivePeersState passivePeers = state.get(PassivePeersState.class);
				Peer peer = response.getPeer();
				EUID peerNid = peer.getSystem().getNID();
				LogicalClockCursor peerCursor = response.getCursor();
				int responseSize = response.getCommitments().size();
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Received iterative discovery response from %s with %s items", peer, responseSize));
				}

				// update last known cursor
				boolean isLatest = updateCursor(peerNid, peerCursor);
				Stream<TempoAction> continuedActions = Stream.empty();
				// if the peer is still selected as a passive peer, continue
				if (passivePeers.contains(peerNid)) {
					// if there is more to synchronise, request more immediately
					if (isLatest && peerCursor.hasNext()) {
						continuedActions = Stream.of(new RequestIterativeSyncAction(peer, peerCursor.getNext(), true));
						if (logger.hasLevel(Logging.DEBUG)) {
							logger.debug(String.format("Continuing iterative discovery with %s at %d", peer, peerCursor.getNext().getLcPosition()));
						}
					} else { // if synchronised, back off exponentially
						int timeout = 1 << cursorDiscovery.getBackoff(peerNid);
						continuedActions = Stream.of(
							new OnDiscoveryCursorSynchronisedAction(peerNid),
							new InitiateIterativeDiscoveryAction(peer).delay(timeout, TimeUnit.SECONDS)
						);
						if (logger.hasLevel(Logging.DEBUG)) {
							logger.debug(String.format("Backing off from iterative discovery with %s for %d seconds as all synced up", peer, timeout));
						}
					}
				}

				Stream<TempoAction> discoveryActions = Stream.empty();
				if (!response.getAids().isEmpty()) {
					discoveryActions = Stream.of(new RequestDeliveryAction(response.getAids(), peer));
				}
				return Stream.concat(discoveryActions, continuedActions);
			}, IterativeDiscoveryState.class, PassivePeersState.class);

		flow.of(ResetAction.class)
			.forEach(reset -> {
				latestCursorStore.reset();
				commitmentStore.reset();
			});

		return TempoFlow.merge(
			reselectPeers,
			initiateDiscovery,
			requestIterativeSync,
			timeoutCursorRequests,
			receiveCursorRequests,
			receiveCursorResponses
		);
	}

	private boolean updateCursor(EUID peerNid, LogicalClockCursor peerCursor) {
		LogicalClockCursor nextCursor = peerCursor.hasNext() ? peerCursor.getNext() : peerCursor;
		long latestCursor = latestCursorStore.get(peerNid, CursorType.DISCOVERY).orElse(-1L);
		// store new cursor if higher than current
		if (nextCursor.getLcPosition() > latestCursor) {
			latestCursorStore.put(peerNid, CursorType.DISCOVERY, nextCursor.getLcPosition());
		}
		// return whether this new cursor was the latest
		return nextCursor.getLcPosition() >= latestCursor;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private EUID self;
		private Supplier<ShardSpace> shardSpaceSupplier;
		private AtomStoreView storeView;
		private LogicalClockCursorStore cursorStore;
		private CommitmentStore commitmentStore;

		private Builder() {
		}

		public Builder self(EUID self) {
			this.self = self;
			return this;
		}

		public Builder storeView(AtomStoreView storeView) {
			this.storeView = storeView;
			return this;
		}

		public Builder shardSpaceSupplier(Supplier<ShardSpace> shardSpaceSupplier) {
			this.shardSpaceSupplier = shardSpaceSupplier;
			return this;
		}

		public Builder cursorStore(LogicalClockCursorStore cursorStore) {
			this.cursorStore = cursorStore;
			return this;
		}

		public Builder commitmentStore(CommitmentStore commitmentStore) {
			this.commitmentStore = commitmentStore;
			return this;
		}

		public IterativeDiscoveryEpic build() {
			Objects.requireNonNull(storeView, "storeView is required");
			Objects.requireNonNull(shardSpaceSupplier, "shardSpaceSupplier is required");
			Objects.requireNonNull(cursorStore, "cursorStore is required");
			Objects.requireNonNull(commitmentStore, "commitmentStore is required");
			Objects.requireNonNull(self, "self is required");

			return new IterativeDiscoveryEpic(self, storeView, shardSpaceSupplier, cursorStore, commitmentStore);
		}
	}
}
