package com.radixdlt.ledger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.radixdlt.atoms.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor.Type;

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
	public List<Atom> delete(AID AID) throws IOException;
	public List<Atom> replace(AID AID, Atom atom) throws IOException;
	public boolean store(Atom atom) throws IOException;
	
	public LedgerCursor search(Type type, LedgerIndexable indexable, SearchMode mode) throws IOException;
	
	public void resolve(Consumer<Atom> callback, Atom ... atoms);
}
