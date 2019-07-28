package com.radixdlt.ledger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.radixdlt.atoms.Atom;
import com.radixdlt.common.AID;

public interface LedgerInterface
{
	public static enum SearchMode
	{
		EXACT, RANGE
	}

	public void register(UniqueIndexablesCreator uniqueIndexablesCreator);
	public void register(DuplicateIndexablesCreator duplicateIndexablesCreator);
	
	public Atom poll();
	public Atom poll(long duration, TimeUnit unit) throws InterruptedException;
	
	public Atom get(AID AID) throws IOException;
	public void delete(AID AID) throws IOException;
	public void replace(AID AID, Atom atom) throws IOException;
	public void store(Atom atom) throws IOException;
	
	public LedgerCursor search(LedgerIndexable indexable, SearchMode mode) throws IOException;
	public LedgerCursor search(LedgerIndexable indexable, long offset) throws IOException;
	
	public void resolve(Consumer<Atom> callback, Atom ... atoms);
}
