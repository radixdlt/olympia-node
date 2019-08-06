package com.radixdlt.tempo;

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
import com.radixdlt.tempo.exceptions.TempoException;
import org.radix.exceptions.ValidationException;
import org.radix.modules.Module;
import org.radix.modules.Plugin;
import org.radix.state.State;
import org.radix.state.StateDomain;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * The Tempo implementation of a ledger.
 */
public final class Tempo extends Plugin implements Ledger {
	private final AtomSynchroniser synchroniser;
	private final AtomStore store;
	private final ConflictResolver resolver;

	private final Supplier<Long> wallclockTimeSupplier;
	private final LocalSystem localSystem;

	private Tempo(AtomSynchroniser synchroniser, AtomStore store, ConflictResolver resolver, Supplier<Long> wallclockTimeSupplier, LocalSystem localSystem) {
		this.synchroniser = Objects.requireNonNull(synchroniser, "synchroniser is required");
		this.store = Objects.requireNonNull(store, "store is required");
		this.resolver = Objects.requireNonNull(resolver, "resolver is required");
		this.wallclockTimeSupplier = wallclockTimeSupplier;
		this.localSystem = localSystem;
	}

	public static Tempo from(AtomSynchroniser synchroniser, AtomStore store, ConflictResolver resolver) {
		return new Tempo(
			synchroniser,
			store,
			resolver,
			Time::currentTimestamp,
			LocalSystem.getInstance()
		);
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
			throw new TempoException("Error while attesting to atom " + tempoAtom.getAID(), ex);
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
			throw new TempoException("Error while attesting to atom " + tempoAtom.getAID(), ex);
		}

		if (store.replace(aids, tempoAtom, uniqueIndices, duplicateIndices)) {
			synchroniser.synchronise(tempoAtom);
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
	public LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) {
		return store.search(type, index, mode);
	}

	// TODO simple temporary function for attestation within this basic Tempo stub
	private void attestTo(TempoAtom atom) throws CryptoException, ValidationException {
		TemporalVertex existingNIDVertex = atom.getTemporalProof().getVertexByNID(this.localSystem.getNID());
		if (existingNIDVertex != null) {
			if (existingNIDVertex.getClock() > this.localSystem.getClock().get()) {
				this.localSystem.set(existingNIDVertex.getClock(), existingNIDVertex.getCommitment(), atom.getTimestamp());
			}

			return;
		}

		long wallclockTime = wallclockTimeSupplier.get();
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
