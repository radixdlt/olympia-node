package com.radixdlt.tempo;

import java.io.IOException;
import java.util.Objects;

import org.bouncycastle.util.Arrays;
import org.radix.atoms.AtomStore;
import org.radix.modules.Modules;

import com.radixdlt.common.AID;
import com.radixdlt.ledger.LedgerCursor;

public class TempoCursor implements LedgerCursor
{
	private Type type;
	private byte[] primary;
	private byte[] indexable;
	
	public TempoCursor(Type type, byte[] primary, byte[] indexable)
	{
		this.type = type;
		this.primary = Arrays.clone(Objects.requireNonNull(primary));
		this.indexable = Arrays.clone(Objects.requireNonNull(indexable));
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

	public byte[] getIndexable()
	{
		return this.indexable;
	}

	@Override
	public AID get()
	{
		return AID.from(this.primary, Long.BYTES);
	}

	@Override
	public LedgerCursor next() throws IOException
	{
		return Modules.get(AtomStore.class).getNext(this);
	}

	@Override
	public LedgerCursor previous() throws IOException
	{
		return Modules.get(AtomStore.class).getPrev(this);
	}

	@Override
	public LedgerCursor first() throws IOException
	{
		return Modules.get(AtomStore.class).getFirst(this);
	}

	@Override
	public LedgerCursor last() throws IOException
	{
		return Modules.get(AtomStore.class).getLast(this);
	}
}
