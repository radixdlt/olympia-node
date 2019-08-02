package com.radixdlt.ledger;

import java.util.Set;
import org.radix.atoms.Atom;

public interface UniqueIndexablesCreator {
	void createUniqueIndexables(Atom atom, Set<LedgerIndexable> uniqueIndexables);
}
