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
import com.radixdlt.tempo.sync.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.RequestDeliveryAction;
import com.radixdlt.tempo.sync.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.sync.actions.SendIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.SendIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.UpdatePassivePeersAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.shards.ShardRange;
import org.radix.shards.ShardRange;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class IterativeSyncEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync Iterative");
	private static final int MAX_BACKOFF = 5; // results in 2^5 -> 32 seconds
	private static final int RESPONSE_AID_LIMIT = 64;

	private final AtomStoreView storeView;
	private final Supplier<ShardRange> shardRangeSupplier;

	// TODO lastCursor should be persisted
	private final Map<EUID, IterativeCursor> lastCursor;
	private final Map<EUID, IterativeSyncState> syncState;
	private final Map<EUID, Integer> backoffCounter;

	private ImmutableSet<EUID> passivePeerNids;

	private IterativeSyncEpic(AtomStoreView storeView, Supplier<ShardRange> shardRangeSupplier) {
		this.storeView = storeView;
		this.shardRangeSupplier = shardRangeSupplier;
		this.lastCursor = new ConcurrentHashMap<>();
		this.syncState = new ConcurrentHashMap<>();
		this.backoffCounter = new ConcurrentHashMap<>();
		this.passivePeerNids = ImmutableSet.of();
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof UpdatePassivePeersAction) {
			ImmutableSet<Peer> passivePeers = ((UpdatePassivePeersAction) action).getPassivePeers();
			this.passivePeerNids = passivePeers.stream()
				.map(peer -> peer.getSystem().getNID())
				.collect(ImmutableSet.toImmutableSet());
			// request synchronisation to all new passive peers
			return passivePeers.stream()
				.filter(peer -> syncState.putIfAbsent(peer.getSystem().getNID(), IterativeSyncState.SYNCHRONISING) != IterativeSyncState.SYNCHRONISING)
				.map(RequestIterativeSyncAction::new);
		} else if (action instanceof RequestIterativeSyncAction) {
			Peer peer = ((RequestIterativeSyncAction) action).getPeer();
			IterativeCursor lastCursor = this.lastCursor.getOrDefault(peer.getSystem().getNID(), IterativeCursor.INITIAL);

			// reset cursor if cursor is ahead of current peer logical clock
			if (lastCursor.getLogicalClockPosition() > peer.getSystem().getClock().get()) {
				lastCursor = IterativeCursor.INITIAL;
			}

			// send iterative request for aids starting with last cursor
			ShardRange shardRange = shardRangeSupplier.get();
			return Stream.of(new SendIterativeRequestAction(shardRange, lastCursor, peer));
		} else if (action instanceof ReceiveIterativeRequestAction) {
			ReceiveIterativeRequestAction request = (ReceiveIterativeRequestAction) action;

			// retrieve and send back aids starting from the requested cursor up to the limit
			Pair<ImmutableList<AID>, IterativeCursor> aidsAndNext = storeView.getNext(request.getCursor(), RESPONSE_AID_LIMIT, request.getShards());
			return Stream.of(new SendIterativeResponseAction(aidsAndNext.getFirst(), aidsAndNext.getSecond(), request.getPeer()));
		} else if (action instanceof ReceiveIterativeResponseAction) {
			ReceiveIterativeResponseAction response = (ReceiveIterativeResponseAction) action;
			Peer peer = response.getPeer();
			EUID peerNid = peer.getSystem().getNID();
			IterativeCursor peerCursor = response.getCursor();
			// update last known cursor
			lastCursor.put(peerNid, peerCursor);

			Stream<SyncAction> continuedActions = Stream.empty();
			// if the peer is still selected as a passive peer, continue synchronisation
			if (passivePeerNids.contains(peerNid)) {
				// if there is more to synchronise, request more immediately
				if (peerCursor.hasNext()) {
					syncState.put(peerNid, IterativeSyncState.SYNCHRONISING);
					backoffCounter.put(peerNid, 0);
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer));
				} else { // if synchronised, back off exponentially
					syncState.put(peerNid, IterativeSyncState.SYNCHRONISED);
					int backoff = backoffCounter.compute(peerNid, (n, c) -> c == null ? 1 : Math.max(MAX_BACKOFF, c + 1));
					int timeout = 1 << backoff;
					continuedActions = Stream.of(new RequestIterativeSyncAction(peer).schedule(timeout, TimeUnit.SECONDS));
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
		private Supplier<ShardRange> shardSpaceSupplier;
		private AtomStoreView storeView;

		private Builder() {
		}

		public Builder storeView(AtomStoreView storeView) {
			this.storeView = storeView;
			return this;
		}

		public Builder shardSpaceSupplier(Supplier<ShardRange> shardSpaceSupplier) {
			this.shardSpaceSupplier = shardSpaceSupplier;
			return this;
		}

		public IterativeSyncEpic build() {
			Objects.requireNonNull(storeView, "storeView is required");
			Objects.requireNonNull(shardSpaceSupplier, "shardRangeSupplier is required");

			return new IterativeSyncEpic(storeView, shardSpaceSupplier);
		}
	}
}
