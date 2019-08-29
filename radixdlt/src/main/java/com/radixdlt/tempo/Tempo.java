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
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.SystemProfiler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo extends Plugin implements Ledger {
	private static final Logger log = Logging.getLogger("Tempo");

	private final AtomStore store;
	private final TempoController controller;
	private final EdgeSelector edgeSelector;
	private final PeerSupplier peerSupplier;
	private final Attestor attestor;

	private final IterativeDiscoveryController iterativeDiscovery;
	private final AtomDeliveryController delivery;

	private Tempo(AtomStore store,
	              TempoController controller,
	              EdgeSelector edgeSelector,
	              PeerSupplier peerSupplier,
	              Attestor attestor,
	              IterativeDiscoveryController iterativeDiscovery,
	              AtomDeliveryController delivery) {
		this.store = store;
		this.controller = controller;
		this.edgeSelector = edgeSelector;
		this.peerSupplier = peerSupplier;
		this.attestor = attestor;
		this.iterativeDiscovery = iterativeDiscovery;
		this.delivery = delivery;

		// hook up components
		// TODO remove listeners?
		this.iterativeDiscovery.addListener(delivery::deliver);
		this.delivery.addListener(((atom, peer) -> {
			// TODO hook up to receive / inbound queue
			log.info("Got delivery of atom '" + atom.getAID() + "' from " + peer);
		}));
	}

	@Override
	public Atom receive() throws InterruptedException {
		return this.controller.receive();
	}

	@Override
	public Optional<Atom> get(AID aid) {
		// cast to abstract atom
		return store.get(aid).map(atom -> atom);
	}

	@Override
	public boolean store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		if (store.contains(tempoAtom.getAID())) {
			return false;
		}
		tempoAtom = attestTo(tempoAtom);
		if (store.store(tempoAtom, uniqueIndices, duplicateIndices)) {
			controller.accept(tempoAtom);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean delete(AID aid) {
		return store.delete(aid);
	}

	@Override
	public boolean replace(Set<AID> aids, Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		TempoAtom tempoAtom = convertToTempoAtom(atom);
		tempoAtom = attestTo(tempoAtom);
		if (store.replace(aids, tempoAtom, uniqueIndices, duplicateIndices)) {
			controller.accept(tempoAtom);
			return true;
		} else {
			return false;
		}
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
		return store.search(type, index, mode);
	}

	@Override
	public List<Class<? extends Module>> getDependsOn() {
		return ImmutableList.of(
			DatabaseEnvironment.class
		);
	}

	@Override
	public void start_impl() {
		this.store.open();
		Modules.put(AtomStoreView.class, this.store);
		Modules.put(AtomSyncView.class, new AtomSyncView() {
			@Override
			public void receive(org.radix.atoms.Atom atom) {
				TempoAtom tempoAtom = LegacyUtils.fromLegacyAtom(atom);
				controller.queue(tempoAtom);
			}

			@Override
			public AtomStatus getAtomStatus(AID aid) {
				return store.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;
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
		this.store.close();
		// nothing to do
	}

	@Override
	public void reset_impl() {
		this.store.reset();
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
		TempoAtomStore store = new TempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		TempoAttestor attestor = new TempoAttestor(localSystem, Time::currentTimestamp);
		return builder()
			.attestor(attestor::attestTo)
			.store(store);
	}

	public static Builder defaultBuilder() {
		LocalSystem localSystem = LocalSystem.getInstance();
		TempoAtomStore store = new TempoAtomStore(
			Serialization.getDefault(),
			SystemProfiler.getInstance(),
			localSystem,
			() -> Modules.get(DatabaseEnvironment.class));
		SingleThreadedScheduler scheduler = new SingleThreadedScheduler();
		IterativeDiscoveryController iterativeDiscovery = new IterativeDiscoveryController(
			localSystem.getNID(),
			store,
			Modules.get(DatabaseEnvironment.class),
			scheduler,
			Modules.get(MessageCentral.class),
			new LegacyAddressBookAdapter(() -> Modules.get(PeerHandler.class), Events.getInstance())
		);
		AtomDeliveryController delivery = new AtomDeliveryController(
			scheduler,
			Modules.get(MessageCentral.class),
			store
		);

		return builder()
			.attestor(new TempoAttestor(localSystem, Time::currentTimestamp)::attestTo)
			.peerSupplier(new PeerSupplierAdapter(() -> Modules.get(PeerHandler.class)))
			.edgeSelector(new SimpleEdgeSelector())
			.store(store)
			.iterativeDiscovery(iterativeDiscovery)
			.delivery(delivery)
			.controller(TempoController.defaultBuilder(store).build());
	}

	public static class Builder {
		private AtomStore store;
		private TempoController controller;
		private Attestor attestor;
		private PeerSupplier peerSupplier;
		private EdgeSelector edgeSelector;
		private IterativeDiscoveryController iterativeDiscovery;
		private AtomDeliveryController delivery;

		public Builder store(AtomStore store) {
			this.store = store;
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
			Objects.requireNonNull(store, "store is required");
			Objects.requireNonNull(controller, "controller is required");
			Objects.requireNonNull(edgeSelector, "edgeSelector is required");
			Objects.requireNonNull(peerSupplier, "peerSupplier is required");
			Objects.requireNonNull(attestor, "attestor is required");
			Objects.requireNonNull(iterativeDiscovery, "iterativeDiscovery is required");
			Objects.requireNonNull(delivery, "delivery is required");

			return new Tempo(
				store,
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
