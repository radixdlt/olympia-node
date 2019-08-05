package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.DuplicateIndexCreator;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndex;
import com.radixdlt.ledger.LedgerSearchMode;
import com.radixdlt.ledger.UniqueIndexCreator;
import org.radix.atoms.Atom;

import java.io.IOException;
import java.util.List;

/**
 * An entry-point for manipulating the state of a Tempo ledger.
 */
public interface AtomStore {
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

	boolean contains(AID aid) throws IOException;

	/**
	 * Gets the atom associated with a certain {@link AID}.
	 */
	Atom get(AID aid) throws IOException;

	List<Atom> delete(AID aid) throws IOException;

	List<Atom> replace(AID aid, Atom atom) throws IOException;

	boolean store(Atom atom) throws IOException;

	LedgerCursor search(LedgerCursor.Type type, LedgerIndex index, LedgerSearchMode mode) throws IOException;
}
