package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.tempo.sync.PeerSupplier;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.ReselectPassivePeersAction;
import com.radixdlt.tempo.sync.actions.UpdatePassivePeersAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class PassivePeerEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync Peers");

	private final int reselectionDelaySeconds;
	private final int reselectionIntervalSeconds;
	private final int desiredPeerCount;
	private final PeerSupplier peerSupplier;

	private PassivePeerEpic(int reselectionDelaySeconds, int reselectionIntervalSeconds, int desiredPeerCount, PeerSupplier peerSupplier) {
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
			ImmutableSet<Peer> passivePeers = peerSupplier.getPeers().stream()
				.limit(desiredPeerCount)
				.collect(ImmutableSet.toImmutableSet());
			logger.debug("Passive peers changed to " + passivePeers);

			return Stream.of(new UpdatePassivePeersAction(passivePeers));
		}

		return Stream.empty();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private int reselectionDelaySeconds = 1;
		private int reselectionIntervalSeconds = 10;
		private int desiredPeerCount = 16;
		private PeerSupplier peerSupplier;
		
		private Builder() {
		}
		
		public Builder peerSupplier(PeerSupplier peerSupplier) {
			this.peerSupplier = peerSupplier;
			return this;
		}

		public PassivePeerEpic build() {
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");

			return new PassivePeerEpic(reselectionDelaySeconds, reselectionIntervalSeconds, desiredPeerCount, peerSupplier);
		}
	}
}
