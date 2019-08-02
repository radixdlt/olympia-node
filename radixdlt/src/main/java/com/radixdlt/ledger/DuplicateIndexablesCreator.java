package com.radixdlt.ledger;

import java.util.Set;
import org.radix.atoms.Atom;

public interface DuplicateIndexablesCreator {
	void createDuplicateIndexables(Atom atom, Set<LedgerIndexable> duplicateIndexables);
}
