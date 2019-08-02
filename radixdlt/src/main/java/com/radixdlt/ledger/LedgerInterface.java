package com.radixdlt.ledger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor.Type;
import java.util.function.Consumer;
import org.radix.atoms.Atom;

public interface LedgerInterface {
	enum SearchMode {
		EXACT, RANGE
	}

	void register(UniqueIndexablesCreator uniqueIndexablesCreator);
	void register(DuplicateIndexablesCreator duplicateIndexablesCreator);
	
	Atom poll();
	Atom poll(long duration, TimeUnit unit) throws InterruptedException;
	
	Atom get(AID AID) throws IOException;
	List<Atom> delete(AID AID) throws IOException;
	List<Atom> replace(AID AID, Atom atom) throws IOException;
	boolean store(Atom atom) throws IOException;
	
	LedgerCursor search(Type type, LedgerIndexable indexable, SearchMode mode) throws IOException;
	
	void resolve(Consumer<Atom> callback, Atom ... atoms);
}
