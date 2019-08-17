package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ResetAction;
import com.radixdlt.tempo.state.IterativeDiscoveryState;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendIterativeDiscoveryResponseAction;
import com.radixdlt.tempo.actions.TimeoutIterativeDiscoveryRequestAction;
import com.radixdlt.tempo.store.CommitmentBatch;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IterativeDiscoveryEpic implements TempoEpic {
	private static final int ITERATIVE_REQUEST_TIMEOUT_SECONDS = 5;

	private static final Logger logger = Logging.getLogger("Sync");
	private static final int MAX_BACKOFF = 4; // results in 2^4 -> 16 seconds
	private static final int RESPONSE_AID_LIMIT = 256;
	private static final int RESPONSE_COMMITMENTS_LIMIT = 512;
	private static final int COMMITMENT_BUFFER_SIZE = 256;

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
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			PassivePeersState.class,
			IterativeDiscoveryState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		PassivePeersState passivePeers = bundle.get(PassivePeersState.class);
		IterativeDiscoveryState syncState = bundle.get(IterativeDiscoveryState.class);

		if (action instanceof AcceptAtomAction) {
			TempoAtom atom = ((AcceptAtomAction) action).getAtom();
			TemporalVertex ownVertex = atom.getTemporalProof().getVertexByNID(self);
			if (ownVertex == null) {
				throw new TempoException("Accepted atom '" + atom.getAID() + "' has no vertex by self");
			} else {
				commitmentStore.put(self, ownVertex.getClock(), ownVertex.getCommitment());
			}
		} else if (action instanceof ReselectPassivePeersAction) {
			// TODO is this an okay way of triggering this? could react to stage-changes instead..?
			// initiate iterative discovery with all new passive peers
			List<Pair<Peer, CommitmentBatch>> initiated = passivePeers.peers()
				.filter(peer -> !syncState.contains(peer.getSystem().getNID()))
				.map(peer -> Pair.of(peer, commitmentStore.getLast(peer.getSystem().getNID(), COMMITMENT_BUFFER_SIZE).ensureCapacity(COMMITMENT_BUFFER_SIZE)))
				.collect(Collectors.toList());
			// abandon iterative discovery of no longer relevant passive peers
			List<EUID> abandoned = syncState.peers()
				.filter(nid -> !passivePeers.contains(nid))
				.collect(Collectors.toList());
			if (!initiated.isEmpty() || !abandoned.isEmpty()) {
				logger.info(String.format(
					"Discovered %d new passive peers to initiate sync with, abandoning %d old passive peers",
					initiated.size(), abandoned.size()));
			}
			return Stream.concat(
				initiated.stream().map(pair -> new InitiateIterativeDiscoveryAction(pair.getFirst(), pair.getSecond())),
				abandoned.stream().map(AbandonIterativeDiscoveryAction::new)
			);
		} else if (action instanceof InitiateIterativeDiscoveryAction) {
			Peer peer = ((InitiateIterativeDiscoveryAction) action).getPeer();
			EUID peerNid = peer.getSystem().getNID();
			long lastCursor = this.latestCursorStore.get(peerNid, CursorType.DISCOVERY).orElse(0L);
			logger.info("Initiating iterative discovery with '" + peer + "' from '" + lastCursor + "'");
			return Stream.of(new RequestIterativeSyncAction(peer, new LogicalClockCursor(lastCursor), false));
		} else if (action instanceof RequestIterativeSyncAction) {
			RequestIterativeSyncAction request = (RequestIterativeSyncAction) action;
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
			TimeoutIterativeDiscoveryRequestAction timeout = new TimeoutIterativeDiscoveryRequestAction(peer, request.getCursor());
			return Stream.of(sendRequest, timeout.delay(ITERATIVE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
		} else if (action instanceof TimeoutIterativeDiscoveryRequestAction) {
			// once the timeout has elapsed, check if we got a response
			TimeoutIterativeDiscoveryRequestAction timeout = (TimeoutIterativeDiscoveryRequestAction) action;
			EUID peerNid = timeout.getPeer().getSystem().getNID();
			long requestedLCPosition = timeout.getRequestedCursor().getLcPosition();

			// if no response, decide what to do after timeout
			if (syncState.isPending(peerNid, requestedLCPosition)) {
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
		} else if (action instanceof ReceiveIterativeDiscoveryRequestAction) {
			ReceiveIterativeDiscoveryRequestAction request = (ReceiveIterativeDiscoveryRequestAction) action;
			long lcPosition = request.getCursor().getLcPosition();

			// retrieve and send back commitments starting from the requested cursor up to the limit
			CommitmentBatch commitments = commitmentStore.getNext(self, lcPosition, RESPONSE_COMMITMENTS_LIMIT);
			long nextLcPosition = commitments.getLastPosition();
			LogicalClockCursor nextCursor = null;
			// only set next cursor if the cursor was actually advanced
			if (nextLcPosition > lcPosition) {
				nextCursor = new LogicalClockCursor(nextLcPosition, null);
			}
			LogicalClockCursor responseCursor = new LogicalClockCursor(lcPosition, nextCursor);
			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug(String.format("Responding to iterative discovery request from %s for %d with %d commitments (next=%s)",
					request.getPeer(), lcPosition, commitments.size(), responseCursor.hasNext() ? nextLcPosition : "<none>"));
			}
			return Stream.of(new SendIterativeDiscoveryResponseAction(commitments, responseCursor, request.getPeer()));
		} else if (action instanceof ReceiveIterativeDiscoveryResponseAction) {
			ReceiveIterativeDiscoveryResponseAction response = (ReceiveIterativeDiscoveryResponseAction) action;
			Peer peer = response.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			LogicalClockCursor peerCursor = response.getCursor();
			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug(String.format("Received iterative discovery response from %s with %s commitments", peer, response.getCommitments().size()));
			}

			// store new commitments
			commitmentStore.put(peerNid, response.getCommitments());

			// update last known cursor
			boolean isLatest = updateCursor(peerNid, peerCursor);
			Stream<TempoAction> continuedActions = Stream.empty();
			// if the peer is still selected as a passive peer, continue
			if (passivePeers.contains(peerNid)) {
				// if there is more to synchronise, request more immediately
				if (isLatest && peerCursor.hasNext()) {
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer, peerCursor.getNext(), true));
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Continuing iterative discovery with %s at %d",
							peer, peerCursor.getNext().getLcPosition()));
					}
				} else { // if synchronised, back off exponentially
					int timeout = 1 << syncState.getBackoff(peerNid);
					continuedActions = Stream.of(
						new OnDiscoveryCursorSynchronisedAction(peerNid),
						new InitiateIterativeDiscoveryAction(peer).delay(timeout, TimeUnit.SECONDS)
					);
					if (logger.hasLevel(Logging.DEBUG)) {
						logger.debug(String.format("Backing off from iterative discovery with %s for %d seconds as all synced up", peer, timeout));
					}
				}
			}

			return continuedActions;
		} else if (action instanceof ResetAction) {
			latestCursorStore.reset();
		}

		return Stream.empty();
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
