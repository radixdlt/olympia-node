package com.radixdlt.ledger;

import java.util.Set;

import com.radixdlt.atoms.Atom;

public interface DuplicateIndexablesCreator
{
	public void createDuplicateIndexables(Atom atom, Set<LedgerIndexable> duplicateIndexables);
}
