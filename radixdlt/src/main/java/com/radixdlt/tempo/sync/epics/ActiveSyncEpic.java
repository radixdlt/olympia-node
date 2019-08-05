package com.radixdlt.tempo.sync.epics;

import com.radixdlt.tempo.sync.PeerSupplier;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.ReceiveAtomAction;
import com.radixdlt.tempo.sync.actions.ReceivePushAction;
import com.radixdlt.tempo.sync.actions.SendPushAction;
import com.radixdlt.tempo.sync.actions.SyncAtomAction;
import org.radix.atoms.Atom;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

import java.util.Optional;
import java.util.stream.Stream;

public class ActiveSyncEpic implements SyncEpic {
	private final LocalSystem localSystem;
	private final PeerSupplier peerSupplier;

	private ActiveSyncEpic(LocalSystem localSystem, PeerSupplier peerSupplier) {
		this.localSystem = localSystem;
		this.peerSupplier = peerSupplier;
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (action instanceof SyncAtomAction) {
			Atom atom = ((SyncAtomAction) action).getAtom();
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
