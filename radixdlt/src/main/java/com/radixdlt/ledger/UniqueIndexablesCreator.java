package com.radixdlt.ledger;

import java.util.Set;

import com.radixdlt.atoms.Atom;

public interface UniqueIndexablesCreator
{
	public void createUniqueIndexables(Atom atom, Set<LedgerIndexable> uniqueIndexables);
}
