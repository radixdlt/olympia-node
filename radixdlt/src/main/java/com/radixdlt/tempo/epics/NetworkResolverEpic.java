package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.ReceiveSamplesAction;
import com.radixdlt.tempo.actions.RequestCollectSamplesAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;
import com.radixdlt.tempo.state.ConflictsState;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class NetworkResolverEpic implements TempoEpic {
	private final ConflictDecider decider;

	private static final Logger logger = Logging.getLogger("Conflict");

	public NetworkResolverEpic(ConflictDecider decider) {
		this.decider = decider;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			LivePeersState.class,
			ConflictsState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoStateBundle bundle, TempoAction action) {
		if (action instanceof ResolveConflictAction) {
			ResolveConflictAction conflict = (ResolveConflictAction) action;
			ImmutableSet<AID> allAids = conflict.allAids().collect(ImmutableSet.toImmutableSet());
			EUID conflictId = getConflictId(allAids);

			// whenever a conflict is raised, request new samples
			return Stream.of(new RequestCollectSamplesAction(conflict.getAtom(), allAids, conflictId));
		} else if (action instanceof ReceiveSamplesAction) {
			Collection<TemporalProof> samples = ((ReceiveSamplesAction) action).getSamples();
			EUID tag = ((ReceiveSamplesAction) action).getTag();
			ConflictsState conflictsState = bundle.get(ConflictsState.class);

			// decide on winner using all collected samples
			TemporalProof winningTP = decider.decide(samples);
			TempoAtom winningAtom = conflictsState.getAtom(tag, winningTP.getAID());
			Set<AID> allConflictingAids = conflictsState.getAids(tag);

			// TODO ugly hack to report back winner to future
			CompletableFuture<TempoAtom> future = conflictsState.getWinnerFutures().get(tag);
			future.complete(winningAtom);

			return Stream.of(new OnConflictResolvedAction(winningAtom, allConflictingAids));
		}

		return Stream.empty();
	}

	private static EUID getConflictId(Set<AID> allConflictingAids) {
		return new EUID(allConflictingAids.stream()
			.map(AID::getLow)
			.reduce(0L, Long::sum));
	}

	interface ConflictDecider {
		TemporalProof decide(Collection<TemporalProof> temporalProofs);
	}
}


