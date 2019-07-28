package com.radixdlt.tempo;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.sleepycat.je.DatabaseEntry;

public class TempoCursor implements LedgerCursor
{
	private DatabaseEntry primary;
	private DatabaseEntry indexable;
	
	TempoCursor(DatabaseEntry primary, DatabaseEntry indexable)
	{
		this.primary = new DatabaseEntry(primary.getData());
		this.indexable = new DatabaseEntry(indexable.getData());
	}

	@Override
	public AID get()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LedgerCursor getNext()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LedgerCursor getPrev()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LedgerCursor getFirst()
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LedgerCursor getLast()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
