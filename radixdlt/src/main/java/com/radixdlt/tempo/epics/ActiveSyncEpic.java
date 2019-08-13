package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.peers.PeerSupplier;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.actions.ReceiveAtomAction;
import com.radixdlt.tempo.actions.ReceivePushAction;
import com.radixdlt.tempo.actions.SendPushAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalVertex;
import org.radix.universe.system.LocalSystem;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class ActiveSyncEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sync");
	private final EUID self;

	public ActiveSyncEpic(EUID self) {
		this.self = self;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(LivePeersState.class);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof AcceptAtomAction) {
			LivePeersState livePeersState = bundle.get(LivePeersState.class);
			TempoAtom atom = ((AcceptAtomAction) action).getAtom();
			TemporalVertex temporalVertex = atom.getTemporalProof().getVertexByNID(self);
			if (temporalVertex != null) {
				return temporalVertex.getEdges().stream()
					.map(livePeersState::getPeer)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.map(peer -> new SendPushAction(atom, peer));
			}
		} else if (action instanceof ReceivePushAction) {
			return Stream.of(new ReceiveAtomAction(((ReceivePushAction) action).getAtom()));
		}

		return Stream.empty();
	}
}
