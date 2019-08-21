package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.RequestIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ResetAction;
import com.radixdlt.tempo.actions.TimeoutCursorDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.reactive.TempoFlow;
import com.radixdlt.tempo.reactive.TempoFlowSource;
import com.radixdlt.tempo.state.IterativeDiscoveryState;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.LogicalClockCursorStore;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("unchecked") // TODO remove warning for varargs
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

	private final LogicalClockCursorStore latestCursorStore;
	private final CommitmentStore commitmentStore;

	private IterativeDiscoveryEpic(EUID self, AtomStoreView storeView, LogicalClockCursorStore cursorStore, CommitmentStore commitmentStore) {
		this.self = self;
		this.storeView = storeView;
		this.latestCursorStore = cursorStore;
		this.commitmentStore = commitmentStore;

		// TODO remove, temporary hack to expose commitment store for debugging
		Modules.put(CommitmentStore.class, commitmentStore);
	}

	@Override
	public TempoFlow<TempoAction> epic(TempoFlowSource flow) {
		flow.of(AcceptAtomAction.class)
			.map(AcceptAtomAction::getAtom)
			.map(atom -> atom.getTemporalProof().getVertexByNID(self)) // TODO may be null
			.forEach(ownVertex -> commitmentStore.put(self, ownVertex.getClock(), ownVertex.getCommitment()));

		// initiate discovery with any new peers
		TempoFlow<TempoAction> initiateDiscovery = flow.of(ReselectPassivePeersAction.class)
			.flatMapStateful((request, state) -> state.get(PassivePeersState.class).peers()
					.filter(peer -> !state.get(IterativeDiscoveryState.class).contains(peer.getSystem().getNID())),
				IterativeDiscoveryState.class, PassivePeersState.class)
			.doOnNext(peer -> logger.info("Discovered new passive peer to initiate iterative discovery with: " + peer))
			.map(InitiateIterativeDiscoveryAction::new);

		// abandon discovery with any old peers
		TempoFlow<TempoAction> abandonDiscovery = flow.of(ReselectPassivePeersAction.class)
			.flatMapStateful((request, state) -> state.get(IterativeDiscoveryState.class).peers()
					.filter(peer -> !state.get(PassivePeersState.class).contains(peer)),
				IterativeDiscoveryState.class, PassivePeersState.class)
			.doOnNext(peer -> logger.info("Discovered old passive peer to abandon iterative discovery with: " + peer))
			.map(AbandonIterativeDiscoveryAction::new);

		// request discovery once initiated
		TempoFlow<TempoAction> requestDiscovery = flow.of(InitiateIterativeDiscoveryAction.class)
			.map(initiate -> new RequestIterativeDiscoveryAction(initiate.getPeer(),
				new LogicalClockCursor(getLatestCursor(initiate.getPeer())), false));

		// send discovery requests
		TempoFlow<TempoAction> sendDiscoveryRequests = flow.of(RequestIterativeDiscoveryAction.class)
			.map(request -> new SendIterativeDiscoveryRequestAction(request.getCursor(), request.getPeer()))
			.doOnNext(send -> {
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Requesting iterative discovery from '%s' starting at '%s'", send.getPeer(), send.getCursor()));
				}
			})
			.flatMap(send -> Stream.of(send,
				new TimeoutCursorDiscoveryRequestAction(send.getCursor(), send.getPeer())
					.delay(CURSOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

		// rerequest discovery when it has timed out if we're still talking to that peer
		TempoFlow<TempoAction> rerequestDiscoveryOnTimeout = flow.of(TimeoutCursorDiscoveryRequestAction.class)
			.filterStateful((timeout, state) -> state.get(IterativeDiscoveryState.class)
				.isPending(timeout.getPeerNid(), timeout.getRequestedCursor().getLcPosition()), IterativeDiscoveryState.class)
			.filterStateful((timeout, state) -> state.get(PassivePeersState.class).contains(timeout.getPeerNid()), PassivePeersState.class)
			.doOnNext(timeout -> {
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Iterative request to %s at %s has timed out without response, resending", timeout.getPeer(), timeout.getRequestedCursor()));
				}
			})
			.map(timeout -> new RequestIterativeDiscoveryAction(timeout.getPeer(), timeout.getRequestedCursor(), false));

		// abandon discovery when it has timed out but we're no longer talking ot that peer
		TempoFlow<TempoAction> abandonDiscoveryOnTimeout = flow.of(TimeoutCursorDiscoveryRequestAction.class)
			.filterStateful((timeout, state) -> state.get(IterativeDiscoveryState.class)
				.isPending(timeout.getPeerNid(), timeout.getRequestedCursor().getLcPosition()), IterativeDiscoveryState.class)
			.filterStateful((timeout, state) -> !state.get(PassivePeersState.class).contains(timeout.getPeerNid()), PassivePeersState.class)
			.doOnNext(timeout -> {
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Iterative request to %s at %s has timed out without response, abandoning", timeout.getPeer(), timeout.getRequestedCursor()));
				}
			})
			.map(timeout -> new AbandonIterativeDiscoveryAction(timeout.getPeerNid()));

		// retrieve and send back aids and commitments starting from the requested cursor up to the limit
		TempoFlow<SendIterativeDiscoveryResponseAction> sendResponses = flow.of(ReceiveIterativeDiscoveryRequestAction.class)
			.map(request -> getDiscoveryResponse(request.getCursor().getLcPosition(), request.getPeer()))
			.doOnNext(response -> {
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Responding to iterative discovery request from %s at %s with %d items",
						response.getPeer(), response.getCursor(), response.getAids().size()));
				}
			});

		// store received commitments
		flow.of(ReceiveIterativeDiscoveryResponseAction.class)
			.forEach(response -> {
				EUID peerNid = response.getPeer().getSystem().getNID();
				commitmentStore.put(peerNid, response.getCommitments(), response.getCursor().getLcPosition());
			});

		// request delivery for aids we got as a response
		TempoFlow<TempoAction> requestDeliveryOnResponse = flow.of(ReceiveIterativeDiscoveryResponseAction.class)
			.filter(response -> !response.getAids().isEmpty())
			.map(response -> new RequestDeliveryAction(response.getAids(), response.getPeer()));

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
				boolean isLatest = updateCursor(peer, peerCursor);
				Stream<TempoAction> continuedActions = Stream.empty();
				// if the peer is still selected as a passive peer, continue
				if (passivePeers.contains(peerNid)) {
					// if there is more to synchronise, request more immediately
					if (isLatest && peerCursor.hasNext()) {
						continuedActions = Stream.of(new RequestIterativeDiscoveryAction(peer, peerCursor.getNext(), true));
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
			initiateDiscovery,
			abandonDiscovery,
			requestDiscovery,
			sendDiscoveryRequests,
			rerequestDiscoveryOnTimeout,
			abandonDiscoveryOnTimeout,
			sendResponses,
			requestDeliveryOnResponse,
			receiveCursorResponses
		);
	}

	private SendIterativeDiscoveryResponseAction getDiscoveryResponse(long lcPosition, Peer peer) {
		// TODO Commitments may be larger than aids as aid may have been deleted but commitments remain.
		// TODO This does not cause any immediate issues but should be addressed in the long run.
		ImmutableList<Hash> commitments = commitmentStore.getNext(self, lcPosition, RESPONSE_LIMIT);
		ImmutableList<AID> aids = storeView.getNext(lcPosition, RESPONSE_LIMIT);

		long nextLcPosition = lcPosition + commitments.size();
		LogicalClockCursor nextCursor = null;
		// only set next cursor if the cursor was actually advanced
		if (nextLcPosition > lcPosition) {
			nextCursor = new LogicalClockCursor(nextLcPosition, null);
		}
		LogicalClockCursor responseCursor = new LogicalClockCursor(lcPosition, nextCursor);
		return new SendIterativeDiscoveryResponseAction(commitments, aids, responseCursor, peer);
	}

	private long getLatestCursor(Peer peer) {
		return this.latestCursorStore.get(peer.getSystem().getNID()).orElse(0L);
	}

	private boolean updateCursor(Peer peer, LogicalClockCursor peerCursor) {
		EUID peerNid = peer.getSystem().getNID();
		LogicalClockCursor nextCursor = peerCursor.hasNext() ? peerCursor.getNext() : peerCursor;
		long latestCursor = getLatestCursor(peer);
		// store new cursor if higher than current
		if (nextCursor.getLcPosition() > latestCursor) {
			latestCursorStore.put(peerNid, nextCursor.getLcPosition());
		}
		// return whether this new cursor was the latest
		return nextCursor.getLcPosition() >= latestCursor;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private EUID self;
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
			Objects.requireNonNull(cursorStore, "cursorStore is required");
			Objects.requireNonNull(commitmentStore, "commitmentStore is required");
			Objects.requireNonNull(self, "self is required");

			return new IterativeDiscoveryEpic(self, storeView, cursorStore, commitmentStore);
		}
	}
}
