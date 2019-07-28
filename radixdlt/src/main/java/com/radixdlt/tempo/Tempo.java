package com.radixdlt.tempo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.radix.atoms.AtomStore;
import org.radix.atoms.PreparedAtom;
import org.radix.database.exceptions.DatabaseException;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;

import com.radixdlt.atoms.Atom;
import com.radixdlt.common.AID;
import com.radixdlt.ledger.DuplicateIndexablesCreator;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.ledger.LedgerIndexable;
import com.radixdlt.ledger.LedgerInterface;
import com.radixdlt.ledger.UniqueIndexablesCreator;
import com.sleepycat.je.DatabaseEntry;

public final class Tempo extends Plugin implements LedgerInterface
{
	private UniqueIndexablesCreator uniqueIndexablesCreator;
	private DuplicateIndexablesCreator duplicateIndexablesCreator;
	
	private final BlockingQueue<Atom> pollQueue = new LinkedBlockingQueue<Atom>();
	
	@Override
	public List<Class<? extends Module>> getComponents()
	{
		List<Class<? extends Module>> dependencies = new ArrayList<>();
		return Collections.unmodifiableList(dependencies);
	}

	@Override
	public void start_impl() throws ModuleException
	{ 
		this.pollQueue.clear();
	}

	@Override
	public void stop_impl() throws ModuleException
	{ }

	@Override
	public String getName() 
	{ 
		return "Tempo"; 
	}
	
	@Override
	public void register(UniqueIndexablesCreator uniqueIndexablesCreator)
	{
		this.uniqueIndexablesCreator = Objects.requireNonNull(uniqueIndexablesCreator);
	}

	@Override
	public void register(DuplicateIndexablesCreator duplicateIndexablesCreator)
	{
		this.duplicateIndexablesCreator = Objects.requireNonNull(duplicateIndexablesCreator);
	}

	@Override
	public Atom poll()
	{
		return this.pollQueue.poll();
	}

	@Override
	public Atom poll(long duration, TimeUnit unit) throws InterruptedException
	{
		return this.pollQueue.poll(duration, unit);
	}

	@Override
	public Atom get(AID AID) throws DatabaseException
	{
		return null;
	}

	@Override
	public void delete(AID AID) throws DatabaseException
	{
	}

	@Override
	public void replace(AID AID, Atom atom) throws DatabaseException
	{
	}

	@Override
	public void store(Atom atom) throws DatabaseException
	{
	}

	@Override
	public void resolve(Consumer<Atom> callback, Atom... atoms)
	{
		// TODO Auto-generated method stub
	}

	@Override
	public LedgerCursor search(LedgerIndexable indexable, SearchMode mode)
	{
		return null;
	}

	@Override
	public LedgerCursor search(LedgerIndexable indexable, long offset)
	{
		return null;
	}
}
