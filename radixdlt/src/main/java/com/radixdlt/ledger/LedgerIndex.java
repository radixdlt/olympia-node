package com.radixdlt.ledger;

import java.util.Objects;

import org.bouncycastle.util.Arrays;

import com.radixdlt.utils.Ints;

public final class LedgerIndex
{
	private final byte 		prefix;		// TODO put this back to an int ... changed to byte for compatibility with legacy AtomStore IDType
	private final byte[] 	identifier;
	
	public static byte[] from(byte prefix, byte[] identifier)
	{
		return Arrays.concatenate(new byte[] {prefix}, identifier);
	}
	
	public LedgerIndex(byte prefix, byte[] identifier)
	{
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier);
	}

	public LedgerIndex(byte[] key)
	{
		Objects.requireNonNull(key);
		
		this.prefix = key[0];
		this.identifier = Arrays.copyOfRange(key, 1, key.length);
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
		return from(this.prefix, this.identifier);
	}
}
