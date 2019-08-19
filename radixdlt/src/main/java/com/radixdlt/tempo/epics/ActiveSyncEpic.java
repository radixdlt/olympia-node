package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoFlow;
import com.radixdlt.tempo.reactive.TempoState;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.actions.ReceiveAtomAction;
import com.radixdlt.tempo.actions.messaging.ReceivePushAction;
import com.radixdlt.tempo.actions.messaging.SendPushAction;
import com.radixdlt.tempo.actions.AcceptAtomAction;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalVertex;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class ActiveSyncEpic implements TempoEpic {
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
	public Stream<TempoAction> epic(TempoFlow flow) {
		Stream<SendPushAction> sendPushes = flow.ofStateful(AcceptAtomAction.class, LivePeersState.class)
			.flatMap(requestWithState -> {
				LivePeersState livePeersState = requestWithState.getBundle().get(LivePeersState.class);
				TempoAtom atom = requestWithState.getAction().getAtom();
				TemporalVertex temporalVertex = atom.getTemporalProof().getVertexByNID(self);
				if (temporalVertex != null) {
					return temporalVertex.getEdges().stream()
						.map(livePeersState::getPeer)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.map(peer -> new SendPushAction(atom, peer));
				} else {
					return Stream.empty();
				}
			});
		Stream<ReceiveAtomAction> receivePushes = flow.of(ReceivePushAction.class)
			.map(push -> new ReceiveAtomAction(push.getAtom()));

		return Streams.concat(
			sendPushes,
			receivePushes
		);
	}
}
