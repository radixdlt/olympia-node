package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.engine.AtomStatus;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.LedgerIndexType;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.delivery.AtomDeliverer;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo extends Plugin implements Ledger {
	private static final Logger log = Logging.getLogger("Tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final EUID self;
	private final TempoAtomStore atomStore;
	private final CommitmentStore commitmentStore;

	private final EdgeSelector edgeSelector;
	private final Attestor attestor;

	private final BlockingQueue<TempoAtom> inboundAtoms;

	private final Set<Resource> ownedResources;
	private final Set<AtomDiscoverer> atomDiscoverers;
	private final Set<AtomDeliverer> atomDeliverers;
	private final RequestDeliverer requestDeliverer;
	private final Set<AtomAcceptor> acceptors;

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
	    Set<AtomAcceptor> acceptors
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
		this.acceptors = acceptors;

		this.inboundAtoms = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

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
	public Atom receive() throws InterruptedException {
		return this.inboundAtoms.take();
	}

	@Override
	public Optional<Atom> get(AID aid) {
		// cast to abstract atom
		return atomStore.get(aid).map(atom -> atom);
	}

	@Override
	public boolean store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		if (atomStore.contains(tempoAtom.getAID())) {
			return false;
		}
		tempoAtom = attestTo(tempoAtom);
		if (atomStore.store(tempoAtom, uniqueIndices, duplicateIndices)) {
			accept(tempoAtom);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean delete(AID aid) {
		return atomStore.delete(aid);
	}

	@Override
	public boolean replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		tempoAtom = attestTo(tempoAtom);
		if (atomStore.replace(aids, tempoAtom, uniqueIndices, duplicateIndices)) {
			accept(tempoAtom);
			return true;
		} else {
			return false;
		}
	}

	private void addInbound(TempoAtom atom) {
		if (!this.inboundAtoms.add(atom)) {
			// TODO more graceful queue full handling
			log.error("Inbound atoms queue full");
		}
	}

	private void accept(TempoAtom atom) {
		TemporalVertex ownVertex = atom.getTemporalProof().getVertexByNID(self);
		if (ownVertex == null) {
			throw new TempoException("Accepted atom " + atom.getAID() + " has no vertex by self");
		}
		commitmentStore.put(self, ownVertex.getClock(), ownVertex.getCommitment());
		acceptors.forEach(acceptor -> acceptor.accept(atom));
	}

	private TempoAtom attestTo(TempoAtom atom) {
		List<EUID> edges = edgeSelector.selectEdges(atom);
		TemporalProof attestedTP = attestor.attestTo(atom.getTemporalProof(), edges);
		return atom.with(attestedTP);
	}

	@Override
	public CompletableFuture<Atom> resolve(Atom atom, Collection<Atom> conflictingAtoms) {
		log.info(String.format("Resolving conflict between '%s' and '%s'", atom.getAID(), conflictingAtoms.stream()
			.map(Atom::getAID)
			.collect(Collectors.toList())));

		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LedgerCursor search(LedgerIndexType type, LedgerIndex index, LedgerSearchMode mode) {
		return atomStore.search(type, index, mode);
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
				return inboundAtoms.size();
			}

			@Override
			public Map<String, Object> getMetaData() {
				return ImmutableMap.of(
					"inboundQueue", inboundAtoms.size()
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
