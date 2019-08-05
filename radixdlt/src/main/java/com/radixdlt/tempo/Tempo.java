package com.radixdlt.tempo;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.AID;
import com.radixdlt.common.EUID;
import com.radixdlt.common.Pair;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.DuplicateIndexCreator;
import com.radixdlt.ledger.Ledger;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerCursor.Type;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.UniqueIndexCreator;
import org.radix.atoms.Atom;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Tempo implementation of a ledger
 */
public final class Tempo extends Plugin implements Ledger {
	private final AtomSynchroniser synchroniser;
	private final AtomStore store;
	private final ConflictResolver resolver;

	private final Function<Atom, List<Atom>> dependentsMapper;
	private final Supplier<Long> wallclockTimeSupplier;
	private final LocalSystem localSystem;

	private Tempo(AtomSynchroniser synchroniser, AtomStore store, ConflictResolver resolver, Function<Atom, List<Atom>> dependentsMapper, Supplier<Long> wallclockTimeSupplier, LocalSystem localSystem) {
		this.synchroniser = Objects.requireNonNull(synchroniser, "synchroniser is required");
		this.store = Objects.requireNonNull(store, "store is required");
		this.resolver = Objects.requireNonNull(resolver, "resolver is required");
		this.dependentsMapper = dependentsMapper;
		this.wallclockTimeSupplier = wallclockTimeSupplier;
		this.localSystem = localSystem;
	}

	public static Tempo from(AtomSynchroniser synchroniser, AtomStore store, ConflictResolver resolver) {
		return new Tempo(
			synchroniser,
			store,
			resolver,
			atom -> ImmutableList.of(), // TODO replace with proper dependents mapper
			Time::currentTimestamp,
			LocalSystem.getInstance()
		);
	}

	@Override
	public void register(UniqueIndexCreator uniqueIndexCreator) {
		this.store.register(uniqueIndexCreator);
	}

	@Override
	public void register(DuplicateIndexCreator duplicateIndexCreator) {
		this.store.register(duplicateIndexCreator);
	}

	@Override
	public Atom receive() throws InterruptedException {
		return this.synchroniser.receive();
	}

	@Override
	public Optional<Atom> get(AID aid) throws IOException {
		return store.get(aid);
	}

	@Override
	public List<Atom> delete(AID aid) throws IOException {
		return store.delete(aid);
	}

	@Override
	public List<Atom> replace(AID aid, Atom atom) throws IOException {
		if (store.contains(atom.getAID())) {
			return Collections.emptyList();
		}

		try {
			attestTo(atom);
		} catch (ValidationException | CryptoException ex) {
			throw new IOException(ex);
		}

		return store.replace(aid, atom);
	}

	@Override
	public boolean store(Atom atom) throws IOException {
		if (store.contains(atom.getAID())) {
			return false;
		}

		try {
			attestTo(atom);
		} catch (ValidationException | CryptoException ex) {
			throw new IOException(ex);
		}

		if (store.store(atom)) {
			synchroniser.synchronise(atom);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public Future<Atom> resolve(Set<Atom> conflictingAtoms) {
		return resolver.resolve(conflictingAtoms);
	}

	@Override
	public LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) throws IOException {
		return store.search(type, index, mode);
	}

	// TODO simple temporary function for attestation within this basic Tempo stub
	private void attestTo(Atom atom) throws ValidationException, CryptoException {
		TemporalVertex existingNIDVertex = atom.getTemporalProof().getVertexByNID(this.localSystem.getNID());
		if (existingNIDVertex != null) {
			if (existingNIDVertex.getClock() > this.localSystem.getClock().get()) {
				this.localSystem.set(existingNIDVertex.getClock(), existingNIDVertex.getCommitment(), atom.getTimestamp());
			}

			return;
		}

		long wallclockTime = wallclockTimeSupplier.get();
		Pair<Long, Hash> clockAndCommitment = this.localSystem.update(atom.getAID(), wallclockTime);
		Set<EUID> edges = Collections.emptySet();
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
		atom.getTemporalProof().add(vertex, nodeKey);
		atom.getTemporalProof().setState(StateDomain.VALIDATION, new State(State.COMPLETE));
	}


	@Override
	public List<Class<? extends Module>> getDependsOn() {
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		dependencies.add(org.radix.atoms.AtomStore.class);
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() {
		this.synchroniser.clear();
	}

	@Override
	public void stop_impl() {
		// nothing to do
	}

	@Override
	public String getName() {
		return "Tempo";
	}
}
