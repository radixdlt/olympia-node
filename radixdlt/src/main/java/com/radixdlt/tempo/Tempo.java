package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.engine.AtomStatus;
import com.radixdlt.ledger.AtomObservation;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex.LedgerIndexType;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.exceptions.AtomAlreadyExistsException;
import com.radixdlt.ledger.exceptions.LedgerIndexConflictException;
import com.radixdlt.tempo.delivery.AtomDeliverer;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.store.AtomConflict;
import com.radixdlt.tempo.store.AtomStoreResult;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.store.TempoAtomStoreView;
import org.radix.database.DatabaseEnvironment;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Tempo implementation of a ledger.
 */
// TODO remove Plugin dependency from Tempo
public final class Tempo extends Plugin implements Ledger {
	private static final Logger log = Logging.getLogger("tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final EUID self;
	private final TempoAtomStore atomStore;
	private final CommitmentStore commitmentStore;

	private final EdgeSelector edgeSelector;
	private final Attestor attestor;

	private final BlockingQueue<AtomObservation> atomObservations;

	private final Set<Resource> ownedResources;
	private final Set<AtomDiscoverer> atomDiscoverers;
	private final Set<AtomDeliverer> atomDeliverers;
	private final RequestDeliverer requestDeliverer;
	private final Set<AtomObserver> observers;

	@Inject
	public Tempo(
		@Named("self") EUID self,
	    TempoAtomStore atomStore,
	    CommitmentStore commitmentStore,
	    EdgeSelector edgeSelector,
	    Attestor attestor,
	    @Owned Set<Resource> ownedResources,
	    Set<AtomDiscoverer> atomDiscoverers,
	    Set<AtomDeliverer> atomDeliverers,
	    RequestDeliverer requestDeliverer,
	    Set<AtomObserver> observers
	) {
		this.self = self;
		this.atomStore = atomStore;
		this.commitmentStore = commitmentStore;
		this.ownedResources = ownedResources;
		this.edgeSelector = edgeSelector;
		this.attestor = attestor;
		this.atomDiscoverers = atomDiscoverers;
		this.atomDeliverers = atomDeliverers;
		this.requestDeliverer = requestDeliverer;
		this.observers = observers;

		this.atomObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		// hook up components
		// TODO remove listeners when closed?
		for (AtomDiscoverer atomDiscoverer : this.atomDiscoverers) {
			atomDiscoverer.addListener(requestDeliverer::tryDeliver);
		}
		for (AtomDeliverer atomDeliverer : atomDeliverers) {
			atomDeliverer.addListener((atom, peer) -> addInbound(atom));
		}
	}

	@Override
	public AtomObservation observe() throws InterruptedException {
		return this.atomObservations.take();
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
		TempoAtom tempoAtom = attestTo(convertToTempoAtom(atom));
		AtomStoreResult status = atomStore.store(tempoAtom, uniqueIndices, duplicateIndices);
		if (!status.isSuccess()) {
			AtomConflict conflictInfo = status.getConflictInfo();
			throw new LedgerIndexConflictException(conflictInfo.getAtom(), conflictInfo.getConflictingAtoms());
		}
		onAdopted(tempoAtom);
	}

	@Override
	public void replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		if (atomStore.contains(atom.getAID())) {
			throw new AtomAlreadyExistsException(atom);
		}
		TempoAtom tempoAtom = attestTo(convertToTempoAtom(atom));
		AtomStoreResult status = atomStore.replace(aids, tempoAtom, uniqueIndices, duplicateIndices);
		if (!status.isSuccess()) {
			AtomConflict conflictInfo = status.getConflictInfo();
			throw new LedgerIndexConflictException(conflictInfo.getAtom(), conflictInfo.getConflictingAtoms());
		}
		aids.forEach(this::onDeleted);
		onAdopted(tempoAtom);
	}

	private void onDeleted(AID aid) {
		observers.forEach(observer -> observer.onDeleted(aid));
	}

	private void addInbound(TempoAtom atom) {
		if (!this.atomObservations.add(AtomObservation.receive(atom))) {
			// TODO more graceful queue full handling
			log.error("Inbound atoms queue full");
		}
	}

	private void onAdopted(TempoAtom atom) {
		TemporalVertex ownVertex = atom.getTemporalProof().getVertexByNID(self);
		if (ownVertex == null) {
			throw new TempoException("Accepted atom " + atom.getAID() + " has no vertex by self");
		}
		// TODO move to commitment acceptor
		commitmentStore.put(self, ownVertex.getClock(), ownVertex.getCommitment());
		observers.forEach(acceptor -> acceptor.onAdopted(atom));
	}

	private TempoAtom attestTo(TempoAtom atom) {
		List<EUID> edges = edgeSelector.selectEdges(atom);
		TemporalProof attestedTP = attestor.attestTo(atom.getTemporalProof(), edges);
		return atom.with(attestedTP);
	}

	@Override
	public LedgerCursor search(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return atomStore.search(type, index, mode);
	}

	@Override
	public boolean contains(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return atomStore.contains(type, index, mode);
	}

	@Override
	public List<Class<? extends Module>> getDependsOn() {
		return ImmutableList.of(
			DatabaseEnvironment.class
		);
	}

	@Override
	public void start_impl() {
		this.ownedResources.forEach(Resource::open);
		Modules.put(TempoAtomStoreView.class, this.atomStore);
		Modules.put(AtomSyncView.class, new AtomSyncView() {
			@Override
			public void inject(org.radix.atoms.Atom atom) {
				TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
				addInbound(tempoAtom);
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return atomStore.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;
			}

			@Override
			public long getQueueSize() {
				return atomObservations.size();
			}

			@Override
			public Map<String, Object> getMetaData() {
				return ImmutableMap.of(
					"inboundQueue", atomObservations.size()
				);
			}
		});
	}

	@Override
	public void stop_impl() {
		Modules.remove(TempoAtomStoreView.class);
		Modules.remove(AtomSyncView.class);
		this.ownedResources.forEach(Resource::close);
	}

	@Override
	public void reset_impl() {
		this.ownedResources.forEach(Resource::reset);
	}

	@Override
	public String getName() {
		return "Tempo";
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
				atom.getTimestamp(),
				atom.getShards()
			);
		}
	}
}
