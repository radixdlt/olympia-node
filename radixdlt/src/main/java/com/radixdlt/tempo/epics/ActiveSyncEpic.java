package com.radixdlt.tempo.epics;

import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoFlowSource;
import com.radixdlt.tempo.reactive.TempoFlow;
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
import java.util.stream.Stream;

public final class ActiveSyncEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Sync");
	private final EUID self;

	public ActiveSyncEpic(EUID self) {
		this.self = self;
	}

	@Override
	public TempoFlow<TempoAction> epic(TempoFlowSource flow) {
		TempoFlow<TempoAction> sendPushes = flow.of(AcceptAtomAction.class)
			.flatMapStateful((request, state) -> {
				TempoAtom atom = request.getAtom();
				TemporalVertex temporalVertex = atom.getTemporalProof().getVertexByNID(self);
				if (temporalVertex != null) {
					return temporalVertex.getEdges().stream()
						.map(state.get(LivePeersState.class)::getPeer)
						.filter(Optional::isPresent)
						.map(Optional::get)
						.map(peer -> new SendPushAction(atom, peer));
				} else {
					return Stream.empty();
				}
			}, LivePeersState.class);
		TempoFlow<TempoAction> receivePushes = flow.of(ReceivePushAction.class)
			.map(push -> new ReceiveAtomAction(push.getAtom()));

		return TempoFlow.merge(
			sendPushes,
			receivePushes
		);
	}
}
