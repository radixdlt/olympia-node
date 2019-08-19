package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.tempo.AtomStoreView;
import com.radixdlt.tempo.LogicalClockCursor;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.TempoFlow;
import com.radixdlt.tempo.actions.AbandonIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.actions.InitiateIterativeDiscoveryAction;
import com.radixdlt.tempo.actions.OnDiscoveryCursorSynchronisedAction;
import com.radixdlt.tempo.actions.RequestDeliveryAction;
import com.radixdlt.tempo.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.actions.ResetAction;
import com.radixdlt.tempo.actions.TimeoutCursorDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveCursorDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceiveCursorDiscoveryResponseAction;
import com.radixdlt.tempo.actions.messaging.ReceivePositionDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.ReceivePositionDiscoveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendCursorDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendCursorDiscoveryResponseAction;
import com.radixdlt.tempo.actions.messaging.SendPositionDiscoveryRequestAction;
import com.radixdlt.tempo.actions.messaging.SendPositionDiscoveryResponseAction;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.reactive.TempoState;
import com.radixdlt.tempo.state.CursorDiscoveryState;
import com.radixdlt.tempo.state.PassivePeersState;
import com.radixdlt.tempo.state.PositionDiscoveryState;
import com.radixdlt.tempo.store.CommitmentBatch;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.LogicalClockCursorStore;
import com.radixdlt.tempo.store.LogicalClockCursorStore.CursorType;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardSpace;
import org.radix.time.TemporalVertex;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IterativeDiscoveryEpic implements TempoEpic {
	// timeout for cursor requests
	private static final int CURSOR_TIMEOUT_SECONDS = 5;
	// timeout for position requests
	private static final int POSITION_TIMEOUT_SECONDS = 5;
	// how many aids to send per response
	private static final int RESPONSE_AID_LIMIT = 512;
	// maximum backoff when synchronised (exponential, e.g. 2^4 = 16 seconds)
	private static final int MAX_BACKOFF = 4;
	// how many commitments to send per response (32 bytes for commitment + 8 bytes for position)
	private static final int RESPONSE_COMMITMENTS_LIMIT = 512;
	// buffer sized to contain all bits of an AID, equal to commitment length
	private static final int COMMITMENT_BUFFER_SIZE = 256;
	// 1000 tps for 10 years gives a 1.71e-7 collision probability for 64 bits, so should be okay
	private static final int COMMITMENT_CERTAINTY_THRESHOLD = 64; // must be multiple of 8

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
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			PassivePeersState.class,
			CursorDiscoveryState.class,
			PositionDiscoveryState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoFlow flow) {
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
		Stream<TempoAction> reselectPeers = flow.ofStateful(ReselectPassivePeersAction.class, CursorDiscoveryState.class, PassivePeersState.class)
			.flatMap(requestWithState -> {
				CursorDiscoveryState cursorDiscovery = requestWithState.getBundle().get(CursorDiscoveryState.class);
				PassivePeersState passivePeers = requestWithState.getBundle().get(PassivePeersState.class);
				// TODO is this an okay way of triggering this? could react to stage-changes instead..?
				// initiate iterative discovery with all new passive peers
				List<Pair<Peer, CommitmentBatch>> initiated = passivePeers.peers()
					.filter(peer -> !cursorDiscovery.contains(peer.getSystem().getNID()))
					.map(peer -> Pair.of(peer, commitmentStore.getLast(peer.getSystem().getNID(), COMMITMENT_BUFFER_SIZE).ensureCapacity(COMMITMENT_BUFFER_SIZE)))
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
					initiated.stream().map(pair -> new InitiateIterativeDiscoveryAction(pair.getFirst(), pair.getSecond())),
					abandoned.stream().map(AbandonIterativeDiscoveryAction::new)
				);
			});

		Stream<RequestIterativeSyncAction> initiateDiscovery = flow.of(InitiateIterativeDiscoveryAction.class)
			.map(initiate -> {
				Peer peer = initiate.getPeer();
				EUID peerNid = peer.getSystem().getNID();
				long lastCursor = this.latestCursorStore.get(peerNid, CursorType.DISCOVERY).orElse(0L);
				logger.info("Initiating iterative discovery with '" + peer + "' from '" + lastCursor + "'");
				return new RequestIterativeSyncAction(peer, new LogicalClockCursor(lastCursor), false);
			});

		// TODO flowify
		// TODO change iterative "sync" to proper name
		Stream<TempoAction> requestIterativeSync = flow.of(RequestIterativeSyncAction.class)
			.flatMap(request -> {
				Peer peer = request.getPeer();
				long requestedLCPosition = request.getCursor().getLcPosition();
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Requesting iterative discovery from '%s' starting at '%d'",
						peer, requestedLCPosition));
				}
				// send iterative request for aids starting with last cursor
				ShardSpace shardRange = shardSpaceSupplier.get();
				SendCursorDiscoveryRequestAction sendRequest = new SendCursorDiscoveryRequestAction(shardRange, request.getCursor(), peer);
				// schedule timeout after which response will be checked
				TimeoutCursorDiscoveryRequestAction timeout = new TimeoutCursorDiscoveryRequestAction(request.getCursor(), peer);
				return Stream.of(sendRequest, timeout.delay(CURSOR_TIMEOUT_SECONDS, TimeUnit.SECONDS));
			});

		// TODO flowify!
		Stream<TempoAction> timeoutCursorRequests = flow.ofStateful(TimeoutCursorDiscoveryRequestAction.class, PassivePeersState.class, CursorDiscoveryState.class)
			.flatMap(timeoutWithState -> {
				CursorDiscoveryState cursorDiscovery = timeoutWithState.getBundle().get(CursorDiscoveryState.class);
				PassivePeersState passivePeers = timeoutWithState.getBundle().get(PassivePeersState.class);

				// once the timeout has elapsed, check if we got a response
				TimeoutCursorDiscoveryRequestAction timeout = timeoutWithState.getAction();
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
			});

		// TODO flowify
		Stream<SendCursorDiscoveryResponseAction> receiveCursorRequests = flow.of(ReceiveCursorDiscoveryRequestAction.class)
			.map(request -> {
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
				return new SendCursorDiscoveryResponseAction(commitments, responseCursor, request.getPeer());
			});

		// TODO flowify, breakup!
		Stream<TempoAction> receiveCursorResponses = flow.ofStateful(ReceiveCursorDiscoveryResponseAction.class, PositionDiscoveryState.class, CursorDiscoveryState.class, PassivePeersState.class)
			.flatMap(responseWithState -> {
				CursorDiscoveryState cursorDiscovery = responseWithState.getBundle().get(CursorDiscoveryState.class);
				PositionDiscoveryState positionDiscovery = responseWithState.getBundle().get(PositionDiscoveryState.class);
				PassivePeersState passivePeers = responseWithState.getBundle().get(PassivePeersState.class);
				ReceiveCursorDiscoveryResponseAction response = responseWithState.getAction();
				Peer peer = response.getPeer();
				EUID peerNid = peer.getSystem().getNID();
				LogicalClockCursor peerCursor = response.getCursor();
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug(String.format("Received iterative discovery response from %s with %s commitments", peer, response.getCommitments().size()));
				}

				// store new commitments
				commitmentStore.put(peerNid, response.getCommitments());

				// send requests to discover unknown positions
				// TODO should probably be extracted to somewhere else
				CommitmentBatch recentCommitments = cursorDiscovery.getRecentCommitments(peerNid);
				ImmutableSet<Long> unknownPositions = getUnknownPositions(recentCommitments, positionDiscovery.getPending(peerNid), storeView::contains);
				Stream<TempoAction> positionDiscoveryActions = Stream.empty();
				if (!unknownPositions.isEmpty()) {
					positionDiscoveryActions = Stream.of(new SendPositionDiscoveryRequestAction(unknownPositions, peer)
						.repeatUntil(POSITION_TIMEOUT_SECONDS, () -> !positionDiscovery.isPending(peerNid, unknownPositions))
					);
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

				return Stream.concat(positionDiscoveryActions, continuedActions);
			});

		Stream<SendPositionDiscoveryResponseAction> receivePositionRequests = flow.of(ReceivePositionDiscoveryRequestAction.class)
			.map(request -> {
				ImmutableMap.Builder<Long, AID> aids = ImmutableMap.builder();
				for (Long position : request.getPositions()) {
					AID aid = storeView.get(position).orElseThrow(()
						-> new TempoException("Peer " + request.getPeer() + " requested non-existent position " + position));
					aids.put(position, aid);
				}
				return new SendPositionDiscoveryResponseAction(aids.build(), request.getPeer());
			});

		Stream<RequestDeliveryAction> receivePositionResponses = flow.of(ReceivePositionDiscoveryResponseAction.class)
			.map(response -> new RequestDeliveryAction(response.getAids().values(), response.getPeer()));

		flow.of(ResetAction.class)
			.forEach(reset -> {
				latestCursorStore.reset();
				commitmentStore.reset();
			});

		return Streams.concat(
			reselectPeers,
			initiateDiscovery,
			requestIterativeSync,
			timeoutCursorRequests,
			receiveCursorRequests,
			receiveCursorResponses,
			receivePositionRequests,
			receivePositionResponses
		);
	}

	// TODO should probably be extracted to elsewhere
	private ImmutableSet<Long> getUnknownPositions(CommitmentBatch batch, Set<Long> excludedPositions, Predicate<byte[]> isPartialAidKnown) {
		int batchSize = batch.size();
		if (batchSize < COMMITMENT_CERTAINTY_THRESHOLD) {
			return ImmutableSet.of();
		} else {
			ImmutableSet.Builder<Long> unknownPositions = ImmutableSet.builder();
			Hash[] commitments = batch.getCommitments();
			long[] positions = batch.getPositions();
			byte[] partialAidBuffer = new byte[COMMITMENT_CERTAINTY_THRESHOLD / Byte.SIZE];
			for (int i = COMMITMENT_CERTAINTY_THRESHOLD - 1; i < batchSize; i++) {
				// if excluded, just skip
				if (excludedPositions.contains(positions[i])) {
					continue;
				}

				// clear buffer because we only set bits (not clear them)
				Arrays.fill(partialAidBuffer, (byte) 0);
				// fill buffer with bits from previous commitments
				// TODO use all (in 8 increments) instead of just past COMMITMENT_CERTAINTY_THRESHOLD
				// TODO need to replace COMMITMENT_CERTAINTY_THRESHOLD - 1 with i and adjust partialAidBuffer
				for (int b = COMMITMENT_CERTAINTY_THRESHOLD - 1; b >= 0; b--) {
					int elementIndex = b >>> 3;
					int bitIndex = b & 7;
					byte[] commitment = commitments[i - b].toByteArray();
					partialAidBuffer[elementIndex] |= (commitment[elementIndex] & 0xff) & (1 << bitIndex);
				}

				if (!isPartialAidKnown.test(partialAidBuffer)) {
					unknownPositions.add(positions[i]);
				}
			}
			return unknownPositions.build();
		}
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
