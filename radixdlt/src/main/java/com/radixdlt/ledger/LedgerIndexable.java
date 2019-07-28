package com.radixdlt.ledger;

import java.util.Objects;

import org.bouncycastle.util.Arrays;

import com.radixdlt.utils.Ints;

public final class LedgerIndexable
{
	private final int prefix;
	private final byte[] identifier;
	
	public LedgerIndexable(int prefix, byte[] identifier)
	{
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier);
	}

	public LedgerIndexable(byte[] key)
	{
		Objects.requireNonNull(key);
		
		this.prefix = Ints.fromByteArray(key);
		this.identifier = Arrays.copyOfRange(key, Integer.SIZE, key.length);
	}

	public int getPrefix()
	{
		return this.prefix;
	}

	public byte[] getIdentifier()
	{
		return this.identifier;
	}
	
	public byte[] getKey()
	{
		return Arrays.concatenate(Ints.toByteArray(this.prefix), this.identifier);
	}
}
