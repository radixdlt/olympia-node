package com.radixdlt.tempo.consensus;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.tempo.AtomObserver;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.TempoAtom;
import com.radixdlt.tempo.store.ConfidenceStore;
import com.radixdlt.tempo.store.SampleStore;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.time.TemporalProof;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class ConsensusController implements AtomObserver {
	private static final Logger log = Logging.getLogger("tempo.consensus");

	// TODO extract to consensusconfiguration
	private static final int CONFIDENCE_THRESHOLD = 3;
	private static final int MAX_SAMPLE_NODES = 5;

	private static final int SAMPLE_NODES_UNAVAILABLE_DELAY = 5000;
	private static final long DELAY_SAMPLE_EMPTY = 500;

	private final Scheduler scheduler;
	private final TempoAtomStoreView storeView;
	private final ConfidenceStore confidenceStore;
	private final SampleStore sampleStore;
	private final SampleRetriever sampleRetriever;
	private final SampleNodeSelector sampleNodeSelector;
	private final AddressBook addressBook;
	private final ConsensusReceptor consensusReceptor;

	private final PendingAtomState pendingAtoms = new PendingAtomState();

	@Inject
	public ConsensusController(
		Scheduler scheduler,
		TempoAtomStoreView storeView,
		ConfidenceStore confidenceStore,
		SampleStore sampleStore,
		SampleRetriever sampleRetriever,
		SampleNodeSelector sampleNodeSelector,
		AddressBook addressBook,
		ConsensusReceptor consensusReceptor
	) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.storeView = Objects.requireNonNull(storeView);
		this.confidenceStore = Objects.requireNonNull(confidenceStore);
		this.sampleStore = Objects.requireNonNull(sampleStore);
		this.sampleRetriever = Objects.requireNonNull(sampleRetriever);
		this.sampleNodeSelector = Objects.requireNonNull(sampleNodeSelector);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.consensusReceptor = Objects.requireNonNull(consensusReceptor);
	}

	// FIXME open resources in constructor so we can just do this in constructor instead
	public void onOpen() {
		Set<AID> uncommitted = confidenceStore.getUncommitted();
		for (AID aid : uncommitted) {
			Optional<TempoAtom> uncommittedAtom = storeView.get(aid);
			if (uncommittedAtom.isPresent()) {
				pendingAtoms.put(uncommittedAtom.get(), storeView.getUniqueIndices(aid));
			} else {
				log.warn("Consensus store contains uncommitted atom '" + aid + "' which no longer exists, removing");
				confidenceStore.delete(aid);
			}
		}
		pendingAtoms.forEachPending(this::continueConsensus);
	}

	private void continueConsensus(TempoAtom preference) {
		log.debug("Continuing consensus for '" + preference.getAID() + "'");

		Set<EUID> availableNids = addressBook.recentPeers()
			.filter(Peer::hasNID)
			.map(Peer::getNID)
			.collect(Collectors.toSet());
		List<EUID> sampleNids = sampleNodeSelector.selectNodes(availableNids, preference, MAX_SAMPLE_NODES);
		if (sampleNids.isEmpty()) {
			log.warn("No sample nodes to talk to, unable to achieve consensus on '" + preference.getAID() + "', waiting");
			scheduler.schedule(() -> continueConsensus(preference), SAMPLE_NODES_UNAVAILABLE_DELAY, TimeUnit.MILLISECONDS);
			return;
		}

		Set<Peer> samplePeers = availableNids.stream()
			.map(addressBook::peer)
			.collect(Collectors.toSet());
		Set<LedgerIndex> uniqueIndices = pendingAtoms.getUniqueIndices(preference.getAID());
		sampleRetriever.sample(uniqueIndices, samplePeers)
			.thenAccept(samples -> receiveSample(preference, uniqueIndices, samples));
	}

	private void receiveSample(TempoAtom preference, Set<LedgerIndex> requestedIndices, Samples samples) {
		log.debug("Received sample for '" + preference.getAID() + "'");
		if (!pendingAtoms.isPending(preference.getAID())) {
			log.debug("Preference '" + preference.getAID() + "' is no longer pending, aborting");
			return;
		}

		// TODO node might have sent index we didn't request, BFD or just ignore
		// aggregate all temporal proofs for all indices of the atom

		// TODO does this even work???
		// temporal proofs may contain stale votes and will keep growing if we use a multi-vote system..
		Map<AID, TemporalProof> temporalProofs = new HashMap<>();
//		for (Sample sample : samples.getSamples()) {
//			for (LedgerIndex index : sample.getTemporalProofsByIndex().keySet()) {
//				if (requestedIndices.contains(index)) {
//					for (Map.Entry<AID, TemporalProof> aidAndTemporalProof : sample.getTemporalProofsByIndex().get(index).entrySet()) {
//
//					}
//					temporalProofs.putAll(sample.getTemporalProofsByIndex().get(index));
//				}
//			}
//		}

		if (temporalProofs.isEmpty()) {
			log.debug("Received empty sample, retrying");
			scheduler.schedule(() -> continueConsensus(preference), DELAY_SAMPLE_EMPTY, TimeUnit.MILLISECONDS);
			return;
		}

		Map<AID, List<EUID>> preferences = MomentumUtils.extractPreferences(temporalProofs.values());
		Map<AID, Long> momenta = MomentumUtils.measure(preferences, nid -> 1L);
		long totalMomenta = momenta.values().stream().mapToLong(l -> l).sum();
		Map.Entry<AID, Long> winner = momenta.entrySet().stream()
			.max(Comparator.comparingLong(Map.Entry::getValue))
			.orElseThrow(IllegalStateException::new);

		if (!winner.getKey().equals(preference.getAID())) {
			log.debug(String.format("Conflicting aid '%s' is in majority (%d/%d), changing preference",
				winner.getKey(), winner.getValue(), totalMomenta));
			change(preference, winner.getKey());
		} else {
			increaseConfidence(preference);
		}
	}

	private void increaseConfidence(TempoAtom preference) {
		int confidence = confidenceStore.increaseConfidence(preference.getAID());
		if (confidence > CONFIDENCE_THRESHOLD) {
			commit(preference);
		} else {
			continueConsensus(preference);
		}
	}

	private void change(TempoAtom oldPreference, AID newPreference) {
		pendingAtoms.remove(oldPreference.getAID());
		confidenceStore.delete(oldPreference.getAID());
		consensusReceptor.change(oldPreference, newPreference);
	}

	private void commit(TempoAtom preference) {
		log.info("Committing to '" + preference.getAID() + "'");
		pendingAtoms.remove(preference.getAID());
		confidenceStore.commit(preference.getAID());
		consensusReceptor.commit(preference);
	}

	@Override
	public void onAdopted(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		sampleStore.add(atom.getTemporalProof());
		pendingAtoms.put(atom, uniqueIndices);
		continueConsensus(atom);
	}

	@Override
	public void onDeleted(AID aid) {
		pendingAtoms.remove(aid);
		confidenceStore.delete(aid);
	}
}
