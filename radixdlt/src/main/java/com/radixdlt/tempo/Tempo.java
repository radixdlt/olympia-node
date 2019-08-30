package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.Atom;
import com.radixdlt.engine.AtomStatus;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.LedgerIndexType;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.tempo.delivery.AtomDeliverer;
import com.radixdlt.tempo.delivery.RequestDeliverer;
import com.radixdlt.tempo.delivery.SingleRequestDeliverer;
import com.radixdlt.tempo.delivery.PushOnlyDeliverer;
import com.radixdlt.tempo.delivery.SingleRequestDelivererConfiguration;
import com.radixdlt.tempo.discovery.AtomDiscoverer;
import com.radixdlt.tempo.discovery.IterativeDiscoverer;
import com.radixdlt.tempo.discovery.IterativeDiscovererConfiguration;
import com.radixdlt.tempo.store.berkeley.BerkeleyCommitmentStore;
import com.radixdlt.tempo.store.berkeley.BerkeleyTempoAtomStore;
import com.radixdlt.tempo.store.CommitmentStore;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.messaging.MessageCentral;
import org.radix.properties.RuntimeProperties;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
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
	private final PeerSupplier peerSupplier;
	private final Attestor attestor;

	private final BlockingQueue<TempoAtom> inboundAtoms;

	private final Set<AtomDiscoverer> atomDiscoverers;
	private final Set<AtomDeliverer> atomDeliverers;
	private final RequestDeliverer requestDeliverer;
	private final Set<Consumer<TempoAtom>> acceptors;

	private Tempo(EUID self,
	              TempoAtomStore atomStore,
	              CommitmentStore commitmentStore,
	              EdgeSelector edgeSelector,
	              PeerSupplier peerSupplier,
	              Attestor attestor,
	              Set<AtomDiscoverer> atomDiscoverers,
	              Set<AtomDeliverer> atomDeliverers,
	              RequestDeliverer requestDeliverer,
	              ImmutableSet<Consumer<TempoAtom>> acceptors
	) {
		this.self = self;
		this.atomStore = atomStore;
		this.commitmentStore = commitmentStore;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;
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
		List<EUID> nids = peerSupplier.getNids();
		List<EUID> edges = edgeSelector.selectEdges(nids, atom);
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
		this.atomStore.open();
		Modules.put(AtomStoreView.class, this.atomStore);
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
		Modules.remove(AtomStoreView.class);
		Modules.remove(AtomSyncView.class);
		this.atomStore.close();
	}

	@Override
	public void reset_impl() {
		this.atomStore.reset();
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

	public static Builder builder() {
		return new Builder();
	}

	public static Builder defaultBuilderStoreOnly() {
		LocalSystem localSystem = LocalSystem.getInstance();
		BerkeleyTempoAtomStore atomStore = new BerkeleyTempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		BerkeleyCommitmentStore commitmentStore = new BerkeleyCommitmentStore(Modules.get(DatabaseEnvironment.class));
		commitmentStore.open();
		TempoAttestor attestor = new TempoAttestor(localSystem, Time::currentTimestamp);
		return builder()
			.self(localSystem.getNID())
			.attestor(attestor::attestTo)
			.atomStore(atomStore)
			.commitmentStore(commitmentStore);
	}

	public static Builder defaultBuilder() {
		LocalSystem localSystem = LocalSystem.getInstance();
		BerkeleyTempoAtomStore atomStore = new BerkeleyTempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		SingleThreadedScheduler scheduler = new SingleThreadedScheduler();
		BerkeleyCommitmentStore commitmentStore = new BerkeleyCommitmentStore(Modules.get(DatabaseEnvironment.class));
		commitmentStore.open();
		PeerSupplierAdapter peerSupplier = new PeerSupplierAdapter(() -> Modules.get(AddressBook.class));
		RuntimeProperties properties = Modules.get(RuntimeProperties.class);
		IterativeDiscoverer iterativeDiscoverer = new IterativeDiscoverer(
			localSystem.getNID(),
			atomStore,
			commitmentStore,
			Modules.get(DatabaseEnvironment.class),
			scheduler,
			Modules.get(MessageCentral.class),
			Events.getInstance(),
			IterativeDiscovererConfiguration.fromRuntimeProperties(properties)
		);
		SingleRequestDeliverer requestDeliverer = new SingleRequestDeliverer(
			scheduler,
			Modules.get(MessageCentral.class),
			atomStore,
			SingleRequestDelivererConfiguration.fromRuntimeProperties(properties)
		);
		PushOnlyDeliverer pushDelivery = new PushOnlyDeliverer(
			localSystem.getNID(),
			Modules.get(MessageCentral.class),
			peerSupplier
		);

		return builder()
			.self(localSystem.getNID())
			.attestor(new TempoAttestor(localSystem, Time::currentTimestamp)::attestTo)
			.peerSupplier(new PeerSupplierAdapter(() -> Modules.get(AddressBook.class)))
			.edgeSelector(new SimpleEdgeSelector())
			.atomStore(atomStore)
			.commitmentStore(commitmentStore)
			.addDiscoverer(iterativeDiscoverer)
			.addDeliverer(requestDeliverer)
			.addDeliverer(pushDelivery)
			.requestDeliverer(requestDeliverer)
			.addAcceptor(pushDelivery::accept);
	}

	public static class Builder {
		private EUID self;
		private TempoAtomStore atomStore;
		private CommitmentStore commitmentStore;
		private Attestor attestor;
		private PeerSupplier peerSupplier;
		private EdgeSelector edgeSelector;
		private RequestDeliverer requestDeliverer;
		private final ImmutableSet.Builder<AtomDiscoverer> atomDiscoverers = ImmutableSet.builder();
		private final ImmutableSet.Builder<AtomDeliverer> atomDeliverers = ImmutableSet.builder();
		private final ImmutableSet.Builder<Consumer<TempoAtom>> atomAcceptors = ImmutableSet.builder();

		public Builder self(EUID self) {
			this.self = self;
			return this;
		}

		public Builder atomStore(TempoAtomStore atomStore) {
			this.atomStore = atomStore;
			return this;
		}

		public Builder commitmentStore(CommitmentStore commitmentStore) {
			this.commitmentStore = commitmentStore;
			return this;
		}

		public Builder attestor(Attestor attestor) {
			this.attestor = attestor;
			return this;
		}

		public Builder edgeSelector(EdgeSelector edgeSelector) {
			this.edgeSelector = edgeSelector;
			return this;
		}

		public Builder peerSupplier(PeerSupplier peerSupplier) {
			this.peerSupplier = peerSupplier;
			return this;
		}

		public Builder addDiscoverer(AtomDiscoverer discoverer) {
			this.atomDiscoverers.add(discoverer);
			return this;
		}

		public Builder addDeliverer(AtomDeliverer deliverer) {
			this.atomDeliverers.add(deliverer);
			return this;
		}

		public Builder requestDeliverer(RequestDeliverer requestDeliverer) {
			this.requestDeliverer = requestDeliverer;
			return this;
		}

		public Builder addAcceptor(Consumer<TempoAtom> acceptor) {
			this.atomAcceptors.add(acceptor);
			return this;
		}

		public Tempo build() {
			Objects.requireNonNull(self, "self is required");
			Objects.requireNonNull(atomStore, "atomStore is required");
			Objects.requireNonNull(commitmentStore, "commitmentStore is required");
			Objects.requireNonNull(edgeSelector, "edgeSelector is required");
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");
			Objects.requireNonNull(attestor, "attestor is required");
			Objects.requireNonNull(requestDeliverer, "requestDeliverer is required");

			return new Tempo(
				self,
				atomStore,
				commitmentStore,
				edgeSelector,
				peerSupplier,
				attestor,
				atomDiscoverers.build(),
				atomDeliverers.build(),
				requestDeliverer,
				atomAcceptors.build());
		}
	}

}
