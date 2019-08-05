package com.radixdlt.ledger;

import java.util.Set;
import org.radix.atoms.Atom;

public interface DuplicateIndexCreator {
	void createDuplicateIndexables(Atom atom, Set<LedgerIndex> duplicateIndexables);
}
