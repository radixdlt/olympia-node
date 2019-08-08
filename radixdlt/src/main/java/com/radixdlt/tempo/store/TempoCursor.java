package com.radixdlt.tempo.store;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;
import com.radixdlt.tempo.AtomStore;
import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.util.Objects;

public class TempoCursor implements LedgerCursor
{
	private final Type type;
	private final byte[] primary;
	private final byte[] index;
	private final TempoAtomStore store;

	public TempoCursor(TempoAtomStore store, Type type, byte[] primary, byte[] index)
	{
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.index = Arrays.clone(Objects.requireNonNull(index));
		this.store = store;
	}

	@Override
	public Type getType()
	{
		return this.type;
	}
	
	public byte[] getPrimary()
	{
		return this.primary;
	}

	public byte[] getIndex()
	{
		return this.index;
	}

	@Override
	public AID get()
	{
		return AID.from(this.primary, Long.BYTES);
	}

	@Override
	public LedgerCursor next() throws IOException
	{
		return this.store.getNext(this);
	}

	@Override
	public LedgerCursor previous() throws IOException
	{
		return this.store.getPrev(this);
	}

	@Override
	public LedgerCursor first() throws IOException
	{
		return this.store.getFirst(this);
	}

	@Override
	public LedgerCursor last() throws IOException
	{
		return this.store.getLast(this);
	}
}
