package com.radixdlt.tempo.sync.epics;

import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.sync.PeerSupplier;
import com.radixdlt.tempo.sync.SyncAction;
import com.radixdlt.tempo.sync.SyncEpic;
import com.radixdlt.tempo.sync.actions.ReceiveAtomAction;
import com.radixdlt.tempo.sync.actions.ReceivePushAction;
import com.radixdlt.tempo.sync.actions.SendPushAction;
import com.radixdlt.tempo.sync.actions.SyncAtomAction;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public class ActiveSyncEpic implements SyncEpic {
	private static final Logger logger = Logging.getLogger("Sync");
	private final LocalSystem localSystem;
	private final PeerSupplier peerSupplier;
	private final BooleanSupplier enabler;

	private ActiveSyncEpic(LocalSystem localSystem, PeerSupplier peerSupplier, BooleanSupplier enabler) {
		this.localSystem = localSystem;
		this.peerSupplier = peerSupplier;
		this.enabler = enabler;
	}

	@Override
	public Stream<SyncAction> epic(SyncAction action) {
		if (!enabler.getAsBoolean()) {
			boolean relevantAction = action instanceof SyncAtomAction || action instanceof ReceivePushAction;
			if (relevantAction && logger.hasLevel(Logging.DEBUG)) {
				logger.debug("Refusing to handle " + action.getClass().getSimpleName() + " because manually disabled");
			}
			return Stream.empty();
		}

		if (action instanceof SyncAtomAction) {
			TempoAtom atom = ((SyncAtomAction) action).getAtom();
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
		private BooleanSupplier enabler = () -> true;

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

		public Builder enabler(BooleanSupplier enabler) {
			this.enabler = enabler;

			return this;
		}

		public ActiveSyncEpic build() {
			return new ActiveSyncEpic(localSystem, peerSupplier, enabler);
		}
	}
}
