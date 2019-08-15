package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.ConflictDecider;
import com.radixdlt.tempo.SampleSelector;
import com.radixdlt.tempo.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.TempoEpic;
import com.radixdlt.tempo.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.ReceiveSamplingResultAction;
import com.radixdlt.tempo.actions.RequestSamplingAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;
import com.radixdlt.tempo.state.ConflictsState;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class NetworkResolverEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Conflict");

	private final SampleSelector sampleSelector;
	private final ConflictDecider decider;

	public NetworkResolverEpic(SampleSelector sampleSelector, ConflictDecider decider) {
		this.sampleSelector = sampleSelector;
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
			LivePeersState livePeers = bundle.get(LivePeersState.class);
			// when a conflict is raised, select some peers to sample
			ImmutableSet<Peer> samplePeers = sampleSelector.selectSamples(livePeers.getNids(), conflict.getAtom()).stream()
				.map(livePeers::getPeer)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(ImmutableSet.toImmutableSet());
			logger.info("Resolving conflict between '" + allAids + ", initiating sampling");
			// sample the selected peers
			return Stream.of(new RequestSamplingAction(samplePeers, allAids, conflict.getTag()));
		} else if (action instanceof ReceiveSamplingResultAction) {
			ReceiveSamplingResultAction result = (ReceiveSamplingResultAction) action;
			Collection<TemporalProof> allSamples = result.getAllSamples();
			EUID tag = result.getTag();
			ConflictsState conflictsState = bundle.get(ConflictsState.class);
			Set<AID> allConflictingAids = conflictsState.getAids(tag);

			TempoAtom winningAtom;
			if (allSamples.isEmpty()) {
				logger.warn("No samples available for any of '" + allConflictingAids +  "', resolving to current preference");
				winningAtom = conflictsState.getCurrentAtom(tag);
			} else {
				// decide on winner using samples
				AID winningAid = decider.decide(allSamples);
				winningAtom = conflictsState.getAtom(tag, winningAid);
			}

			return Stream.of(new OnConflictResolvedAction(winningAtom, allConflictingAids, tag));
		}

		return Stream.empty();
	}

}


