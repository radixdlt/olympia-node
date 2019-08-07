package com.radixdlt.tempo.sync.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeRequestAction;
import com.radixdlt.tempo.sync.actions.ReceiveIterativeResponseAction;
import com.radixdlt.tempo.sync.actions.RequestIterativeSyncAction;
import com.radixdlt.tempo.sync.actions.UpdatePassivePeersAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class IterativeSyncEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync Iterative");

	// TODO lastCursor should be persisted
	private final Map<EUID, LedgerCursor> lastCursor;
	private final Map<EUID, IterativeSyncState> syncState;
	private ImmutableSet<Peer> passivePeers;

	private IterativeSyncEpic() {
		this.lastCursor = new HashMap<>();
		this.syncState = new HashMap<>();
		this.passivePeers = ImmutableSet.of();
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof UpdatePassivePeersAction) {
			passivePeers = ((UpdatePassivePeersAction) action).getPassivePeers();

			return passivePeers.stream()
				.filter(peer -> !syncState.containsKey(peer.getSystem().getNID()))
				.map(RequestIterativeSyncAction::new);
		} else if (action instanceof RequestIterativeSyncAction) {
			Peer peer = ((RequestIterativeSyncAction) action).getPeer();
			syncState.put(peer.getSystem().getNID(), IterativeSyncState.SYNCHRONISING);


		} else if (action instanceof ReceiveIterativeRequestAction) {

		} else if (action instanceof ReceiveIterativeResponseAction) {

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
		private Builder() {
		}

		public IterativeSyncEpic build() {
			return new IterativeSyncEpic();
		}
	}
}
