package com.radixdlt.ledger;

import java.util.Set;
import org.radix.atoms.Atom;

public interface UniqueIndexCreator {
	void createUniqueIndexables(Atom atom, Set<LedgerIndex> uniqueIndexables);
}
