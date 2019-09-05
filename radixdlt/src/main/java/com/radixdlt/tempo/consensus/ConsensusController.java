package com.radixdlt.tempo.consensus;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.AtomObserver;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ConsensusController implements AtomObserver {
	private static final Logger log = Logging.getLogger("consensus");

	// TODO extract to consensusconfiguration
	private static final int CONFIDENCE_THRESHOLD = 3;
	private static final int MAX_SAMPLE_NODES = 5;
	private static final double SAMPLE_SIGNIFICANCE_THRESHOLD = 0.7;

	private static final int SAMPLE_NODES_UNAVAILABLE_DELAY_MILLISECONDS = 1000;

	private final EUID self;
	private final Scheduler scheduler;
	private final TempoAtomStoreView storeView;
	private final AtomConfidence atomConfidence;
	private final SampleRetriever sampleRetriever;
	private final SampleNodeSelector sampleNodeSelector;
	private final AddressBook addressBook;
	private final ConsensusReceptor consensusReceptor;

	private final PendingAtomState pendingAtoms = new PendingAtomState();

	@Inject
	public ConsensusController(
		@Named("self") EUID self,
		Scheduler scheduler,
		TempoAtomStoreView  storeView,
		AtomConfidence atomConfidence,
		SampleRetriever sampleRetriever,
		SampleNodeSelector sampleNodeSelector,
		AddressBook addressBook,
		ConsensusReceptor consensusReceptor
	) {
		this.self = Objects.requireNonNull(self);
		this.scheduler = Objects.requireNonNull(scheduler);
		this.storeView = Objects.requireNonNull(storeView);
		this.atomConfidence = Objects.requireNonNull(atomConfidence);
		this.sampleRetriever = Objects.requireNonNull(sampleRetriever);
		this.sampleNodeSelector = Objects.requireNonNull(sampleNodeSelector);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.consensusReceptor = Objects.requireNonNull(consensusReceptor);

		start();
	}

	private void start() {
		Set<AID> pending = storeView.getPending();
		for (AID aid : pending) {
			Optional<TempoAtom> uncommittedAtom = storeView.get(aid);
			if (uncommittedAtom.isPresent()) {
				pendingAtoms.put(uncommittedAtom.get(), storeView.getUniqueIndices(aid));
			} else {
				log.warn("Consensus store contains uncommitted atom '" + aid + "' which no longer exists, removing");
				atomConfidence.reset(aid);
			}
		}
		pendingAtoms.forEachPending(this::beginRound);
	}

	private void beginRound(TempoAtom preference) {
		log.debug("Beginning consensus round for atom '" + preference.getAID() + "'");
		Set<EUID> availableNids = addressBook.recentPeers()
			.filter(Peer::hasNID)
			.map(Peer::getNID)
			.collect(Collectors.toSet());
		List<EUID> sampleNids = sampleNodeSelector.selectNodes(availableNids, preference, MAX_SAMPLE_NODES);
		if (sampleNids.isEmpty()) {
			log.warn("No sample nodes to talk to, unable to achieve consensus on '" + preference.getAID() + "', waiting");
			scheduler.schedule(() -> beginRound(preference), SAMPLE_NODES_UNAVAILABLE_DELAY_MILLISECONDS, TimeUnit.MILLISECONDS);
			return;
		}

		Set<Peer> samplePeers = availableNids.stream()
			.map(addressBook::peer)
			.collect(Collectors.toSet());
		Set<LedgerIndex> uniqueIndices = pendingAtoms.getUniqueIndices(preference.getAID());
		sampleRetriever.sample(preference.getAID(), uniqueIndices, samplePeers)
			.thenAccept(samples -> endRound(preference, uniqueIndices, sampleNids, samples));
	}

	private void endRound(TempoAtom preference, Set<LedgerIndex> requestedIndices, List<EUID> sampleNids, Samples samples) {
		log.debug("Ending consensus round for atom '" + preference.getAID() + "'");
		if (!pendingAtoms.isPending(preference.getAID())) {
			log.debug("Preference '" + preference.getAID() + "' is no longer pending, aborting");
			return;
		}

		int availableVotes = requestedIndices.size() * sampleNids.size();
		if (!samples.hasTopPreference() || samples.getTopPreferenceCount() < availableVotes * SAMPLE_SIGNIFICANCE_THRESHOLD) {
			// nothing to do if there is no significant top preference, just begin another round
			beginRound(preference);
			return;
		}

		AID topPreference = samples.getTopPreference();
		if (topPreference.equals(preference.getAID())) {
			// if the significant preference matches our current preference, increase confidence
			int confidence = atomConfidence.increaseConfidence(preference.getAID());
			if (confidence > CONFIDENCE_THRESHOLD) {
				// if we have sufficient confidence in our preference, commit to it
				commit(preference);
			} else {
				// if we don't have sufficient confidence yet, begin another round
				beginRound(preference);
			}
		} else {
			// if the significant preference is a different preference, try and change to that
			changePreference(preference, topPreference, samples.getPeersFor(topPreference));
		}
	}

	private void changePreference(TempoAtom oldPreference, AID newPreference, Set<EUID> peersToContact) {
		pendingAtoms.remove(oldPreference.getAID());
		atomConfidence.reset(oldPreference.getAID());
		consensusReceptor.requestChangePreference(oldPreference, newPreference, peersToContact.stream()
			.map(addressBook::peer)
			.collect(Collectors.toSet()));
	}

	private void commit(TempoAtom preference) {
		pendingAtoms.remove(preference.getAID());
		consensusReceptor.requestCommit(preference);
	}

	@Override
	public void onAdopted(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		pendingAtoms.put(atom, uniqueIndices);
		beginRound(atom);
	}

	@Override
	public void onDeleted(AID aid) {
		pendingAtoms.remove(aid);
		atomConfidence.reset(aid);
	}
}
