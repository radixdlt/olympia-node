package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.tempo.conflict.LocalConflictResolver;
import com.radixdlt.tempo.exceptions.TempoException;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.sync.SimpleEdgeSelector;
import com.radixdlt.tempo.sync.TempoAtomSynchroniser;
import org.radix.database.DatabaseEnvironment;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo extends Plugin implements Ledger {
	private static final Logger logger = Logging.getLogger("Tempo");

	private final AtomSynchroniser synchroniser;
	private final AtomStore store;
	private final ConflictResolver resolver;

	private final LongSupplier wallclockTimeSupplier;
	private final LocalSystem localSystem;

	private Tempo(AtomSynchroniser synchroniser, AtomStore store, ConflictResolver resolver, LongSupplier wallclockTimeSupplier, LocalSystem localSystem) {
		this.synchroniser = synchroniser;
		this.store = store;
		this.resolver = resolver;
		this.wallclockTimeSupplier = wallclockTimeSupplier;
		this.localSystem = localSystem;
	}

	private void fail(String message) {
		logger.error(message);
		throw new TempoException(message);
	}

	private void fail(String message, Exception cause) {
		logger.error(message, cause);
		throw new TempoException(message, cause);
	}

	@Override
	public Atom receive() throws InterruptedException {
		return this.synchroniser.receive();
	}

	@Override
	public Optional<Atom> get(AID aid) {
		// cast to abstract atom
		return store.get(aid).map(atom -> (Atom) atom);
	}

	@Override
	public boolean store(Atom atom, Set<LedgerIndex> uniqueIndices, Set<LedgerIndex> duplicateIndices) {
		TempoAtom tempoAtom = (TempoAtom) atom;
		if (store.contains(atom.getAID())) {
			return false;
		}

		try {
			attestTo(tempoAtom);
		} catch (ValidationException | CryptoException ex) {
			fail("Error while attesting to atom " + tempoAtom.getAID(), ex);
		}

		if (store.store(tempoAtom, uniqueIndices, duplicateIndices)) {
			synchroniser.synchronise(tempoAtom);
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
		TempoAtom tempoAtom = (TempoAtom) atom;
		if (store.contains(atom.getAID())) {
			return false;
		}

		try {
			attestTo(tempoAtom);
		} catch (ValidationException | CryptoException ex) {
			fail("Error while attesting to atom " + tempoAtom.getAID(), ex);
		}

		if (store.replace(aids, tempoAtom, uniqueIndices, duplicateIndices)) {
			synchroniser.synchronise(tempoAtom);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public CompletableFuture<Atom> resolve(Atom atom, Set<Atom> conflictingAtoms) {
		logger.info("Resolving conflict between " + atom + " and " + conflictingAtoms);

		return resolver.resolve((TempoAtom) atom, conflictingAtoms.stream()
				.map(TempoAtom.class::cast)
				.collect(Collectors.toSet()))
			.thenApply(Atom.class::cast);
	}

	@Override
	public LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) {
		return store.search(type, index, mode);
	}

	private void attestTo(TempoAtom atom) throws CryptoException, ValidationException {
		TemporalVertex existingNIDVertex = atom.getTemporalProof().getVertexByNID(this.localSystem.getNID());
		if (existingNIDVertex != null) {
			if (existingNIDVertex.getClock() > this.localSystem.getClock().get()) {
				this.localSystem.set(existingNIDVertex.getClock(), existingNIDVertex.getCommitment(), atom.getTimestamp());
			}
			if (logger.hasLevel(Logging.DEBUG)) {
				logger.debug("");
			}

			return;
		}

		long wallclockTime = wallclockTimeSupplier.getAsLong();
		Pair<Long, Hash> clockAndCommitment = this.localSystem.update(atom.getAID(), wallclockTime);
		List<EUID> edges = synchroniser.selectEdges(atom);
		TemporalVertex previousVertex = null;
		if (!atom.getTemporalProof().isEmpty()) {
			for (TemporalVertex vertex : atom.getTemporalProof().getVertices()) {
				if (vertex.getEdges().contains(this.localSystem.getNID())) {
					previousVertex = vertex;
					break;
				} else if (previousVertex == null) {
					previousVertex = vertex;
				}
			}
		}

		ECKeyPair nodeKey = this.localSystem.getKeyPair();
		TemporalVertex vertex = new TemporalVertex(nodeKey.getPublicKey(),
			clockAndCommitment.getFirst(),
			wallclockTime,
			clockAndCommitment.getSecond(),
			previousVertex != null ? previousVertex.getHID() : EUID.ZERO,
			edges);
		if (logger.hasLevel(Logging.DEBUG)) {
			logger.debug("Attesting to '" + atom.getAID() + "' at " + clockAndCommitment.getFirst());
		}
		atom.getTemporalProof().add(vertex, nodeKey);
		// TODO is this still required?
		atom.getTemporalProof().setState(StateDomain.VALIDATION, new State(State.COMPLETE));
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
		this.synchroniser.clear();
		Modules.put(AtomStoreView.class, this.store.asReadOnlyView());
		Modules.put(AtomSyncView.class, this.synchroniser.getLegacyAdapter());
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
	}

	@Override
	public String getName() {
		return "Tempo";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder defaultBuilderWithoutSynchroniser() {
		TempoAtomStore tempoAtomStore = new TempoAtomStore(() -> Modules.get(DatabaseEnvironment.class));
		LocalSystem localSystem = LocalSystem.getInstance();
		return builder()
			.store(tempoAtomStore)
			.resolver(new LocalConflictResolver(localSystem.getNID()))
			.localSystem(localSystem)
			.wallclockTime(Time::currentTimestamp);
	}

	public static Builder defaultBuilder() {
		TempoAtomStore tempoAtomStore = new TempoAtomStore(() -> Modules.get(DatabaseEnvironment.class));
		LocalSystem localSystem = LocalSystem.getInstance();
		return builder()
			.synchroniser(
				TempoAtomSynchroniser.defaultBuilder(tempoAtomStore.asReadOnlyView())
				.edgeSelector(new SimpleEdgeSelector())
				.build()
			)
			.store(tempoAtomStore)
			.resolver(new LocalConflictResolver(localSystem.getNID()))
			.localSystem(localSystem)
			.wallclockTime(Time::currentTimestamp);
	}

	public static class Builder {
		private AtomSynchroniser synchroniser;
		private AtomStore store;
		private ConflictResolver resolver;

		private LongSupplier wallclockTimeSupplier;
		private LocalSystem localSystem;

		public Builder synchroniser(AtomSynchroniser synchroniser) {
			this.synchroniser = synchroniser;
			return this;
		}

		public Builder store(AtomStore store) {
			this.store = store;
			return this;
		}

		public Builder resolver(ConflictResolver resolver) {
			this.resolver = resolver;
			return this;
		}

		public Builder wallclockTime(LongSupplier wallclockTimeSupplier) {
			this.wallclockTimeSupplier = wallclockTimeSupplier;
			return this;
		}

		public Builder localSystem(LocalSystem localSystem) {
			this.localSystem = localSystem;
			return this;
		}

		public Tempo build() {
			Objects.requireNonNull(synchroniser, "synchroniser is required");
			Objects.requireNonNull(store, "store is required");
			Objects.requireNonNull(resolver, "resolver is required");
			Objects.requireNonNull(wallclockTimeSupplier, "wallclockTimeSupplier is required");
			Objects.requireNonNull(localSystem, "localSystem is required");

			return new Tempo(
				synchroniser,
				store,
				resolver,
				wallclockTimeSupplier,
				localSystem
			);
		}
	}
}
