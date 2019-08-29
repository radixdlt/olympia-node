package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.radixdlt.tempo.delivery.AtomDeliveryController;
import com.radixdlt.tempo.discovery.IterativeDiscoveryController;
import com.radixdlt.tempo.store.CommitmentStore;
import com.radixdlt.tempo.store.TempoAtomStore;
import org.json.JSONObject;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.network.peers.PeerHandler;
import org.radix.network2.messaging.MessageCentral;
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
import java.util.stream.Collectors;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo extends Plugin implements Ledger {
	private static final Logger log = Logging.getLogger("Tempo");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final EUID self;
	private final AtomStore atomStore;
	private final CommitmentStore commitmentStore;

	private final TempoController controller;
	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;
	private final Attestor attestor;

	private final BlockingQueue<TempoAtom> inboundAtoms;

	private final IterativeDiscoveryController iterativeDiscovery;
	private final AtomDeliveryController delivery;

	private Tempo(EUID self,
	              AtomStore atomStore,
	              CommitmentStore commitmentStore,
	              TempoController controller,
	              EdgeSelector edgeSelector,
	              PeerSupplier peerSupplier,
	              Attestor attestor,
	              IterativeDiscoveryController iterativeDiscovery,
	              AtomDeliveryController delivery) {
		this.self = self;
		this.atomStore = atomStore;
		this.commitmentStore = commitmentStore;
		this.controller = controller;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;
		this.attestor = attestor;
		this.iterativeDiscovery = iterativeDiscovery;
		this.delivery = delivery;

		this.inboundAtoms = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		// hook up components
		// TODO remove listeners when closed?
		this.iterativeDiscovery.addListener(delivery::deliver);
		this.delivery.addListener(((atom, peer) -> addInbound(atom)));
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

		return controller.resolve(convertToTempoAtom(atom), conflictingAtoms.stream()
			.map(Tempo::convertToTempoAtom)
			.collect(Collectors.toSet()))
			.thenApply(Tempo::convertToTempoAtom);
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
			public void receive(org.radix.atoms.Atom atom) {
				TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
				controller.queue(tempoAtom);
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return atomStore.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;
			}

			@Override
			public long getQueueSize() {
				return controller.getInboundQueueSize();
			}

			@Override
			public Map<String, Object> getMetaData() {
				return ImmutableMap.of(
					"inboundQueue", controller.getInboundQueueSize(),
					"actionQueue", controller.getActionQueueSize()
				);
			}
		});
	}

	@Override
	public void stop_impl() {
		Modules.remove(AtomStoreView.class);
		Modules.remove(AtomSyncView.class);
		this.atomStore.close();
		// nothing to do
	}

	@Override
	public void reset_impl() {
		this.atomStore.reset();
		this.controller.reset();
	}

	@Override
	public String getName() {
		return "Tempo";
	}

	public JSONObject getJsonRepresentation(String stateClassName) {
		return controller.getJsonRepresentation(stateClassName);
	}

	public JSONObject getJsonRepresentation() {
		return controller.getJsonRepresentation();
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
		TempoAtomStore atomStore = new TempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		CommitmentStore commitmentStore = new CommitmentStore(Modules.get(DatabaseEnvironment.class));
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
		TempoAtomStore atomStore = new TempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		SingleThreadedScheduler scheduler = new SingleThreadedScheduler();
		CommitmentStore commitmentStore = new CommitmentStore(Modules.get(DatabaseEnvironment.class));
		commitmentStore.open();
		IterativeDiscoveryController iterativeDiscovery = new IterativeDiscoveryController(
			localSystem.getNID(),
			atomStore,
			commitmentStore,
			Modules.get(DatabaseEnvironment.class),
			scheduler,
			Modules.get(MessageCentral.class),
			new LegacyAddressBookAdapter(() -> Modules.get(PeerHandler.class), Events.getInstance())
		);
		AtomDeliveryController delivery = new AtomDeliveryController(
			scheduler,
			Modules.get(MessageCentral.class),
			atomStore
		);

		return builder()
			.self(localSystem.getNID())
			.attestor(new TempoAttestor(localSystem, Time::currentTimestamp)::attestTo)
			.peerSupplier(new PeerSupplierAdapter(() -> Modules.get(PeerHandler.class)))
			.edgeSelector(new SimpleEdgeSelector())
			.atomStore(atomStore)
			.commitmentStore(commitmentStore)
			.iterativeDiscovery(iterativeDiscovery)
			.delivery(delivery)
			.controller(TempoController.defaultBuilder(atomStore).build());
	}

	public static class Builder {
		private EUID self;
		private AtomStore atomStore;
		private CommitmentStore commitmentStore;
		private TempoController controller;
		private Attestor attestor;
		private PeerSupplier peerSupplier;
		private EdgeSelector edgeSelector;
		private IterativeDiscoveryController iterativeDiscovery;
		private AtomDeliveryController delivery;

		public Builder self(EUID self) {
			this.self = self;
			return this;
		}

		public Builder atomStore(AtomStore atomStore) {
			this.atomStore = atomStore;
			return this;
		}

		public Builder commitmentStore(CommitmentStore commitmentStore) {
			this.commitmentStore = commitmentStore;
			return this;
		}

		public Builder controller(TempoController controller) {
			this.controller = controller;
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

		public Builder iterativeDiscovery(IterativeDiscoveryController iterativeDiscovery) {
			this.iterativeDiscovery = iterativeDiscovery;
			return this;
		}

		public Builder delivery(AtomDeliveryController delivery) {
			this.delivery = delivery;
			return this;
		}

		public Tempo build() {
			Objects.requireNonNull(self, "self is required");
			Objects.requireNonNull(atomStore, "atomStore is required");
			Objects.requireNonNull(commitmentStore, "commitmentStore is required");
			Objects.requireNonNull(controller, "controller is required");
			Objects.requireNonNull(edgeSelector, "edgeSelector is required");
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");
			Objects.requireNonNull(attestor, "attestor is required");
			Objects.requireNonNull(iterativeDiscovery, "iterativeDiscovery is required");
			Objects.requireNonNull(delivery, "delivery is required");

			return new Tempo(
				self,
				atomStore,
				commitmentStore,
				controller,
				edgeSelector,
				peerSupplier,
				attestor,
				iterativeDiscovery,
				delivery
			);
		}
	}

}
