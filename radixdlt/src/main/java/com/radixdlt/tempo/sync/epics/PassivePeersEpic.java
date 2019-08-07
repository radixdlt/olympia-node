package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.sync.PeerSupplier;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.AcceptPassivePeersAction;
import com.radixdlt.tempo.sync.actions.ReselectPassivePeersAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class PassivePeersEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync");

	private final int reselectionDelaySeconds;
	private final int reselectionIntervalSeconds;
	private final int desiredPeerCount;
	private final PeerSupplier peerSupplier;
	private ImmutableSet<EUID> currentPassivePeerNids;

	private PassivePeersEpic(int reselectionDelaySeconds, int reselectionIntervalSeconds, int desiredPeerCount, PeerSupplier peerSupplier) {
		this.reselectionDelaySeconds = reselectionDelaySeconds;
		this.reselectionIntervalSeconds = reselectionIntervalSeconds;
		this.desiredPeerCount = desiredPeerCount;
		this.peerSupplier = peerSupplier;
	}

	@Override
	public Stream<SyncAction> initialActions() {
		return Stream.of(new ReselectPassivePeersAction()
			.repeat(reselectionDelaySeconds, reselectionIntervalSeconds, TimeUnit.SECONDS));
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof ReselectPassivePeersAction) {
			// TODO smarter peer selection function, consider sharding, rebalancing etc
			ImmutableSet<Peer> newPassivePeers = peerSupplier.getPeers().stream()
				.limit(desiredPeerCount)
				.collect(ImmutableSet.toImmutableSet());
			ImmutableSet<EUID> newPassivePeerNids = newPassivePeers.stream()
				.map(peer -> peer.getSystem().getNID())
				.collect(ImmutableSet.toImmutableSet());
			if (currentPassivePeerNids == null || !Sets.difference(currentPassivePeerNids, newPassivePeerNids).isEmpty()) {
				logger.info("Selected passive peers: " + newPassivePeers);
				currentPassivePeerNids = newPassivePeerNids;
				return Stream.of(new AcceptPassivePeersAction(newPassivePeers));
			} else {
				if (logger.hasLevel(Logging.DEBUG)) {
					logger.debug("Reselected passive peers are unchanged");
				}
				return Stream.empty();
			}
		}

		return Stream.empty();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private int reselectionDelaySeconds = 5;
		private int reselectionIntervalSeconds = 10;
		private int desiredPeerCount = 16;
		private PeerSupplier peerSupplier;

		private Builder() {
		}

		public Builder peerSupplier(PeerSupplier peerSupplier) {
			this.peerSupplier = peerSupplier;
			return this;
		}

		public PassivePeersEpic build() {
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");

			return new PassivePeersEpic(reselectionDelaySeconds, reselectionIntervalSeconds, desiredPeerCount, peerSupplier);
		}
	}
}
