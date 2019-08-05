package com.radixdlt.ledger;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor.Type;
import org.radix.atoms.Atom;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * An instance of a ledger which may be synchronised across a set of nodes.
 */
public interface Ledger {
	/**
	 * Receives a new atom from a queue, blocking until an atom becomes available
	 * @return The received atom
	 */
	Atom receive() throws InterruptedException;

	/**
	 * Registers a unique ledger index creator.
	 *
	 * @param uniqueIndexCreator The index creator
	 */
	void register(UniqueIndexCreator uniqueIndexCreator);

	/**
	 * Registers a duplicate ledger index creator.
	 *
	 * @param duplicateIndexCreator The index creator
	 */
	void register(DuplicateIndexCreator duplicateIndexCreator);

	/**
	 * Gets the atom associated with a certain {@link AID}.
	 */
	Atom get(AID aid) throws IOException;

	List<Atom> delete(AID aid) throws IOException;

	List<Atom> replace(AID aid, Atom atom) throws IOException;

	boolean store(Atom atom) throws IOException;

	LedgerCursor search(Type type, LedgerIndex index, LedgerSearchMode mode) throws IOException;

	/**
	 * Resolves a conflict between a non-empty set of atoms, returns the winning atom.
	 *
	 * @param conflictingAtoms The non-empty set of conflicting atoms
	 * @return a {@link Future} yielding the winning atom
	 */
	Future<Atom> resolve(Set<Atom> conflictingAtoms);
}
