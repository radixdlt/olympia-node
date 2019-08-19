package com.radixdlt.tempo.epics;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.tempo.MomentumUtils;
import com.radixdlt.tempo.SampleSelector;
import com.radixdlt.tempo.TempoFlow;
import com.radixdlt.tempo.reactive.TempoAction;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.reactive.TempoActionWithState;
import com.radixdlt.tempo.reactive.TempoEpic;
import com.radixdlt.tempo.TempoException;
import com.radixdlt.tempo.reactive.TempoState;
import com.radixdlt.tempo.TempoStateBundle;
import com.radixdlt.tempo.actions.OnConflictResolvedAction;
import com.radixdlt.tempo.actions.RaiseConflictAction;
import com.radixdlt.tempo.actions.OnSamplingCompleteAction;
import com.radixdlt.tempo.actions.RequestSamplingAction;
import com.radixdlt.tempo.actions.ResolveConflictAction;
import com.radixdlt.tempo.state.ConflictsState;
import com.radixdlt.tempo.state.LivePeersState;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.peers.Peer;
import org.radix.time.TemporalProof;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class MomentumResolverEpic implements TempoEpic {
	private static final Logger logger = Logging.getLogger("Conflicts");

	private final SampleSelector sampleSelector;

	public MomentumResolverEpic(SampleSelector sampleSelector) {
		this.sampleSelector = sampleSelector;
	}

	@Override
	public Set<Class<? extends TempoState>> requiredState() {
		return ImmutableSet.of(
			LivePeersState.class,
			ConflictsState.class
		);
	}

	@Override
	public Stream<TempoAction> epic(TempoFlow flow) {
		Stream<TempoAction> raiseConflicts = flow.ofStateful(RaiseConflictAction.class, ConflictsState.class)
			.filter(conflict
				-> !conflict.getBundle().get(ConflictsState.class).isPending(conflict.getAction().getTag()))
			.map(TempoActionWithState::getAction)
			.map(conflict -> new ResolveConflictAction(conflict.getAtom(), conflict.getConflictingAtoms(), conflict.getTag()));

		// TODO flowify
		Stream<TempoAction> resolveConflicts = flow.ofStateful(ResolveConflictAction.class, LivePeersState.class)
			.map(conflictWithState -> {
				ResolveConflictAction conflict = conflictWithState.getAction();
				LivePeersState livePeers = conflictWithState.getBundle().get(LivePeersState.class);

				// to resolve a conflict, select some peers to sample
				ImmutableSet<Peer> samplePeers = sampleSelector.selectSamples(livePeers.getNids(), conflict.getAtom()).stream()
					.map(livePeers::getPeer)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(ImmutableSet.toImmutableSet());

				ImmutableSet<AID> allAids = conflict.allAids().collect(ImmutableSet.toImmutableSet());
				logger.info(String.format("Resolving conflict with tag '%s' between '%s', initiating sampling",
					conflict.getTag(), allAids));
				// sample the selected peers
				return new RequestSamplingAction(samplePeers, allAids, conflict.getTag());
			});

		// TODO flowify
		Stream<TempoAction> completeConflicts = flow.ofStateful(OnSamplingCompleteAction.class, ConflictsState.class)
			.map(requestWithState -> {
				OnSamplingCompleteAction result = requestWithState.getAction();
				Collection<TemporalProof> allSamples = result.getAllSamples();
				EUID tag = result.getTag();
				ConflictsState conflicts = requestWithState.getBundle().get(ConflictsState.class);
				Set<AID> allConflictingAids = conflicts.getAids(tag);

				TempoAtom winningAtom;
				if (allSamples.isEmpty()) {
					logger.warn("No samples available for any of '" + allConflictingAids + "', resolving to current preference");
					winningAtom = conflicts.getCurrentAtom(tag);
				} else {
					// decide on winner using samples
					Map<AID, List<EUID>> preferences = MomentumUtils.extractPreferences(allSamples);
					Map<AID, Long> momenta = MomentumUtils.measure(preferences, nid -> 1L);
					AID winner = momenta.entrySet().stream()
						.max(Comparator.comparingLong(Map.Entry::getValue))
						.map(Map.Entry::getKey)
						.orElseThrow(() -> new TempoException("Internal error while measuring momenta"));
					logger.info(String.format("Resolved conflict with tag '%s' to %s, measured momenta to be %s for %s",
						result.getTag(), winner, momenta, allConflictingAids));
					winningAtom = conflicts.getAtom(tag, winner);
				}

				return new OnConflictResolvedAction(winningAtom, allConflictingAids, tag);
			});

		return Streams.concat(
			raiseConflicts,
			resolveConflicts,
			completeConflicts
		);
	}
}


