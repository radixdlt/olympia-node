package com.radixdlt.tempo.epics;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.peers.PeerSupplier;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.ReceiveAtomAction;
import com.radixdlt.tempo.actions.ReceivePushAction;
import com.radixdlt.tempo.actions.SendPushAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

import java.util.Optional;
import java.util.stream.Stream;

public class ActiveSyncEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sync");
	private final LocalSystem localSystem;
	private final PeerSupplier peerSupplier;

	private ActiveSyncEpic(LocalSystem localSystem, PeerSupplier peerSupplier) {
		this.localSystem = localSystem;
		this.peerSupplier = peerSupplier;
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof AcceptAtomAction) {
			TempoAtom atom = ((AcceptAtomAction) action).getAtom();
			TemporalVertex temporalVertex = atom.getTemporalProof().getVertexByNID(localSystem.getNID());
			if (temporalVertex != null) {
				return temporalVertex.getEdges().stream()
					.map(peerSupplier::getPeer)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(peer -> new SendPushAction(atom, peer));
			}
		} else if (action instanceof ReceivePushAction) {
			return Stream.of(new ReceiveAtomAction(((ReceivePushAction) action).getAtom()));
		}

		return Stream.empty();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private LocalSystem localSystem;
		private PeerSupplier peerSupplier;

		private Builder() {
		}

		public Builder localSystem(LocalSystem localSystem) {
			this.localSystem = localSystem;
			return this;
		}

		public Builder peerSupplier(PeerSupplier peerSupplier) {
			this.peerSupplier = peerSupplier;
			return this;
		}

		public ActiveSyncEpic build() {
			return new ActiveSyncEpic(localSystem, peerSupplier);
		}
	}
}
