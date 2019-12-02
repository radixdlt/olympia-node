package com.radixdlt.tempo.consensus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerEntry;
import com.radixdlt.tempo.LedgerEntryObserver;
import com.radixdlt.tempo.Scheduler;
import com.radixdlt.tempo.delivery.LazyRequestDeliverer;
import com.radixdlt.tempo.store.LedgerEntryStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of random subsampling consensus.
 */
@Singleton
public final class RSSConsensus implements LedgerEntryObserver, Consensus {
	private static final Logger log = Logging.getLogger("consensus");

	// TODO extract to RSSConsensusConfiguration or similar
	private static final int CONFIDENCE_THRESHOLD = 3;
	private static final int MAX_SAMPLE_NODES = 5;
	private static final double SAMPLE_SIGNIFICANCE_THRESHOLD = 0.7;

	private static final int SAMPLE_NODES_UNAVAILABLE_DELAY_MILLISECONDS = 1000;
	private static final int CONSENSUS_ACTION_QUEUE_CAPACITY = 8192;

	private final Scheduler scheduler;
	private final LedgerEntryStoreView storeView;
	private final AtomConfidence atomConfidence;
	private final SampleRetriever sampleRetriever;
	private final LazyRequestDeliverer requestDeliverer;
	private final SampleNodeSelector sampleNodeSelector;
	private final AddressBook addressBook;

	private final BlockingQueue<ConsensusAction> actions;

	private final PendingLedgerEntryState pendingAtoms = new PendingLedgerEntryState();

	@Inject
	public RSSConsensus(
		Scheduler scheduler,
		LedgerEntryStoreView storeView,
		AtomConfidence atomConfidence,
		SampleRetriever sampleRetriever,
		LazyRequestDeliverer requestDeliverer,
		SampleNodeSelector sampleNodeSelector,
		AddressBook addressBook
	) {
		this.scheduler = Objects.requireNonNull(scheduler);
		this.storeView = Objects.requireNonNull(storeView);
		this.atomConfidence = Objects.requireNonNull(atomConfidence);
		this.sampleRetriever = Objects.requireNonNull(sampleRetriever);
		this.requestDeliverer = Objects.requireNonNull(requestDeliverer);
		this.sampleNodeSelector = Objects.requireNonNull(sampleNodeSelector);
		this.addressBook = Objects.requireNonNull(addressBook);

		this.actions = new ArrayBlockingQueue<>(CONSENSUS_ACTION_QUEUE_CAPACITY);

		start();
	}

	private void start() {
		Set<AID> pending = storeView.getPending();
		for (AID aid : pending) {
			Optional<LedgerEntry> uncommittedLedgerEntry = storeView.get(aid);
			if (uncommittedLedgerEntry.isPresent()) {
				pendingAtoms.put(uncommittedLedgerEntry.get(), storeView.getUniqueIndices(aid));
			} else {
				log.warn("Atom store contains pending atom '" + aid + "' which no longer exists, removing");
				atomConfidence.reset(aid);
			}
		}
		pendingAtoms.forEachPending(this::beginRound);
	}

	private void beginRound(LedgerEntry preference) {
		log.debug("Beginning consensus round for atom '" + preference.getAID() + "'");
		List<Peer> samplePeers = sampleNodeSelector.selectNodes(addressBook.recentPeers(), preference, MAX_SAMPLE_NODES);
		if (samplePeers.isEmpty()) {
			log.warn("No sample nodes to talk to, unable to achieve consensus on '" + preference.getAID() + "', waiting");
			scheduler.schedule(() -> beginRound(preference), SAMPLE_NODES_UNAVAILABLE_DELAY_MILLISECONDS, TimeUnit.MILLISECONDS);
			return;
		}

		Set<LedgerIndex> uniqueIndices = pendingAtoms.getUniqueIndices(preference.getAID());
		sampleRetriever.sample(preference.getAID(), uniqueIndices, samplePeers)
			.thenAccept(samples -> endRound(preference, samplePeers, samples));
	}

	private void endRound(LedgerEntry preference, List<Peer> samplePeers, Samples samples) {
		log.debug("Ending consensus round for atom '" + preference.getAID() + "'");

		if (!pendingAtoms.isPending(preference.getAID())) {
			log.debug("Preference '" + preference.getAID() + "' is no longer pending, round result will be ignored");
			return;
		}

		Set<LedgerIndex> indices = pendingAtoms.getUniqueIndices(preference.getAID());
		ConsensusDecision decision = decide(preference, indices, samples);
		log.debug("Decided to " + decision + " for preference '" + preference.getAID() + "'");
		switch (decision) {
			case COMMIT:
				notifyCommit(preference);
				return;
			// TODO need to consider sharding here, what happens if the majority is outside our shard range?
			case SWITCH_TO_MAJORITY:
				throw new UnsupportedOperationException("Cannot switch to majority, no longer supported");
			case CONTINUE:
				beginRound(preference);
				return;
			default:
				throw new IllegalStateException("Unknown consensus decision for preference '" + preference.getAID() + "': " + decision);
		}
	}

	private void notifyCommit(LedgerEntry preference) {
		pendingAtoms.remove(preference.getAID());
		notify(ConsensusAction.commit(preference));
	}

	private void notify(ConsensusAction action) {
		if (!this.actions.add(action)) {
			log.warn("Consensus action queue full, unable to queue " + action);
		}
	}

	// TODO reconsider architecture, move decision elsewhere?
	private ConsensusDecision decide(LedgerEntry preference, Set<LedgerIndex> indices, Samples samples) {
		int availableVotes = indices.size() * samples.getSamplePeerCount();
		if (!samples.hasTopPreference() || samples.getTopPreferenceCount() < availableVotes * SAMPLE_SIGNIFICANCE_THRESHOLD) {
			// reset confidence if there is no majority top preference, then begin another round
			atomConfidence.reset(preference.getAID());
			return ConsensusDecision.CONTINUE;
		}

		AID majorityPreference = samples.getTopPreference();
		if (majorityPreference.equals(preference.getAID())) {
			// if the significant preference matches our current preference, increase confidence
			int confidence = atomConfidence.increaseConfidence(preference.getAID());
			if (confidence > CONFIDENCE_THRESHOLD) {
				// if we have sufficient confidence in our preference, commit to it
				return ConsensusDecision.COMMIT;
			} else {
				// if we don't have sufficient confidence yet, begin another round
				return ConsensusDecision.CONTINUE;
			}
		} else {
			// if the majority preference is a different preference, try and change to that
			atomConfidence.reset(preference.getAID());
			return ConsensusDecision.SWITCH_TO_MAJORITY;
		}
	}

	@Override
	public void onAdopted(LedgerEntry ledgerEntry, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		pendingAtoms.put(ledgerEntry, uniqueIndices);
		beginRound(ledgerEntry);
	}

	@Override
	public void onDeleted(AID aid) {
		pendingAtoms.remove(aid);
		atomConfidence.reset(aid);
	}

	@Override
	public ConsensusAction observe() throws InterruptedException {
		return actions.take();
	}

	public enum ConsensusDecision {
		COMMIT,
		CONTINUE,
		SWITCH_TO_MAJORITY
	}
}
