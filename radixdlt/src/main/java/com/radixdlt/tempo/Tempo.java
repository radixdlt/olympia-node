package com.radixdlt.tempo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.engine.AtomStatus;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerIndex.LedgerIndexType;
import com.radixdlt.ledger.LedgerObservation;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.exceptions.AtomAlreadyExistsException;
import com.radixdlt.ledger.exceptions.LedgerIndexConflictException;
import com.radixdlt.tempo.consensus.Consensus;
import com.radixdlt.tempo.consensus.ConsensusAction;
import com.radixdlt.tempo.delivery.AtomDeliverer;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.store.AtomConflict;
import com.radixdlt.tempo.store.AtomStoreResult;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.utils.SimpleThreadPool;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo implements Ledger, Closeable {
	private static final Logger log = Logging.getLogger("tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final EUID self;
	private final TempoAtomStore atomStore;
	private final CommitmentStore commitmentStore;
	private final Consensus consensus;
	private final Attestor attestor;

	private final Set<Resource> ownedResources;
	private final Set<AtomDiscoverer> atomDiscoverers;
	private final Set<AtomDeliverer> atomDeliverers;
	private final RequestDeliverer requestDeliverer;
	private final Set<AtomObserver> observers; // TODO external ledgerObservations and internal observers is ambiguous

	private final BlockingQueue<LedgerObservation> ledgerObservations;
	private final Map<AID, Set<? extends Atom>> pendingPreferenceChanges = new ConcurrentHashMap<>();
	private final SimpleThreadPool<ConsensusAction> consensusProcessor;

	@Inject
	public Tempo(
		@Named("self") EUID self,
		TempoAtomStore atomStore,
		CommitmentStore commitmentStore,
		Consensus consensus,
		Attestor attestor,
		@Owned Set<Resource> ownedResources,
		Set<AtomDiscoverer> atomDiscoverers,
		Set<AtomDeliverer> atomDeliverers,
		RequestDeliverer requestDeliverer,
		Set<AtomObserver> observers
	) {
		this.self = Objects.requireNonNull(self);
		this.atomStore = Objects.requireNonNull(atomStore);
		this.commitmentStore = Objects.requireNonNull(commitmentStore);
		this.consensus = Objects.requireNonNull(consensus);
		this.attestor = Objects.requireNonNull(attestor);
		this.ownedResources = Objects.requireNonNull(ownedResources);
		this.atomDiscoverers = Objects.requireNonNull(atomDiscoverers);
		this.atomDeliverers = Objects.requireNonNull(atomDeliverers);
		this.requestDeliverer = Objects.requireNonNull(requestDeliverer);
		this.observers = Objects.requireNonNull(observers);

		this.ledgerObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);
		this.consensusProcessor = new SimpleThreadPool<>("Tempo consensus processing", 1, consensus::observe, this::processConsensusAction, log);
		this.consensusProcessor.start();

		// hook up components
		for (AtomDiscoverer atomDiscoverer : this.atomDiscoverers) {
			atomDiscoverer.addListener(requestDeliverer::tryDeliver);
		}
		for (AtomDeliverer atomDeliverer : atomDeliverers) {
			atomDeliverer.addListener((atom, peer) -> onDelivered(atom));
		}
	}

	private void processConsensusAction(ConsensusAction action) {
		if (action.getType() == ConsensusAction.Type.COMMIT) {
			// TODO do something with commitment
			TempoAtom preference = action.getPreference();
			TemporalCommitment temporalCommitment = attestTo(preference);
			log.info("Committing to '" + preference.getAID() + "' at " + temporalCommitment.getLogicalClock());
			this.atomStore.commit(preference.getAID(), temporalCommitment.getLogicalClock());
			this.commitmentStore.put(self, temporalCommitment.getLogicalClock(), temporalCommitment.getCommitment());

			this.injectObservation(LedgerObservation.commit(preference));
		} else if (action.getType() == ConsensusAction.Type.SWITCH_PREFERENCE) {
			AID newPreferenceAid = action.getPreferenceAid();
			if (action.hasPreference()) {
				log.info("Switching preference from '" + action.getOldPreferences() + "' to '" + newPreferenceAid + "'");
				injectObservation(LedgerObservation.adopt(action.getOldPreferences(), action.getPreference()));
			} else {
				log.info("Trying to switch preference from '" + action.getOldPreferences() + "' to '" + newPreferenceAid + "'");
				pendingPreferenceChanges.put(newPreferenceAid, action.getOldPreferences());
				action.getPeersToContact()
					.forEach(peer -> requestDeliverer.tryDeliver(ImmutableSet.of(newPreferenceAid), peer));
			}
		} else {
			throw new IllegalStateException("Unknown consensus action type: " + action.getType());
		}
	}

	@Override
	public LedgerObservation observe() throws InterruptedException {
		return this.ledgerObservations.take();
	}

	@Override
	public Optional<Atom> get(AID aid) {
		// cast to abstract atom
		return atomStore.get(aid).map(atom -> atom);
	}

	@Override
	public void store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		if (atomStore.contains(atom.getAID())) {
			throw new AtomAlreadyExistsException(atom);
		}
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		AtomStoreResult status = atomStore.store(tempoAtom, uniqueIndices, duplicateIndices);
		if (!status.isSuccess()) {
			AtomConflict conflictInfo = status.getConflictInfo();
			throw new LedgerIndexConflictException(conflictInfo.getAtom(), conflictInfo.getConflictingAtoms());
		}
		onAdopted(tempoAtom, uniqueIndices, duplicateIndices);
	}

	@Override
	public void replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		if (atomStore.contains(atom.getAID())) {
			throw new AtomAlreadyExistsException(atom);
		}
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		AtomStoreResult status = atomStore.replace(aids, tempoAtom, uniqueIndices, duplicateIndices);
		if (!status.isSuccess()) {
			AtomConflict conflictInfo = status.getConflictInfo();
			throw new LedgerIndexConflictException(conflictInfo.getAtom(), conflictInfo.getConflictingAtoms());
		}
		aids.forEach(this::onDeleted);
		onAdopted(tempoAtom, uniqueIndices, duplicateIndices);
	}

	private void onDeleted(AID aid) {
		observers.forEach(observer -> observer.onDeleted(aid));
	}

	private void onDelivered(TempoAtom atom) {
		// TODO add shard space relevance check
		Set<? extends Atom> oldPreferences = pendingPreferenceChanges.remove(atom.getAID());
		if (oldPreferences != null) {
			injectObservation(LedgerObservation.adopt(oldPreferences, atom));
		} else {
			injectObservation(LedgerObservation.adopt(atom));
		}
	}

	private void injectObservation(LedgerObservation observation) {
		if (!this.ledgerObservations.add(observation)) {
			// TODO more graceful queue full handling
			log.error("Atom observations queue full");
		}
	}

	private void onAdopted(TempoAtom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		observers.forEach(acceptor -> acceptor.onAdopted(atom, uniqueIndices, duplicateIndices));
	}

	private TemporalCommitment attestTo(TempoAtom atom) {
		return attestor.attestTo(atom.getAID());
	}

	@Override
	public LedgerCursor search(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return atomStore.search(type, index, mode);
	}

	@Override
	public boolean contains(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return atomStore.contains(type, index, mode);
	}

	public void start() {
		Modules.put(TempoAtomStoreView.class, this.atomStore);
		Modules.put(AtomSyncView.class, new AtomSyncView() {
			@Override
			public void inject(org.radix.atoms.Atom atom) {
				TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
				onDelivered(tempoAtom);
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return atomStore.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;
			}

			@Override
			public long getQueueSize() {
				return ledgerObservations.size();
			}

			@Override
			public Map<String, Object> getMetaData() {
				return ImmutableMap.of(
					"inboundQueue", ledgerObservations.size()
				);
			}
		});
	}

	@Override
	public void close() {
		Modules.remove(TempoAtomStoreView.class);
		Modules.remove(AtomSyncView.class);
		this.ownedResources.forEach(Resource::close);
	}

	public void reset() {
		this.ownedResources.forEach(Resource::reset);
	}

	private static TempoAtom convertToTempoAtom(Atom atom) {
		if (atom instanceof TempoAtom) {
			return (TempoAtom) atom;
		} else {
			if (log.hasLevel(Logging.DEBUG)) {
				log.debug("Converting foreign atom '" + atom.getAID() + "' to Tempo atom");
			}
			return new TempoAtom(
				atom.getContent(),
				atom.getAID(),
				atom.getShards()
			);
		}
	}
}
