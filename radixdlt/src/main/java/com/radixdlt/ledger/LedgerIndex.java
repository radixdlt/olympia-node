package com.radixdlt.ledger;

import org.bouncycastle.util.Arrays;

import java.util.Objects;

// TODO change to interface
public final class LedgerIndex {
	// TODO change to int (byte for compatibility with legacy AtomStore IDType)
	private byte prefix;
	private byte[] identifier;

	public static byte[] from(byte prefix, byte[] identifier) {
		return Arrays.concatenate(new byte[]{prefix}, identifier);
	}

	public LedgerIndex(byte prefix, byte[] identifier) {
		this.prefix = prefix;
		this.identifier = Objects.requireNonNull(identifier, "identifier is required");
	}

	public LedgerIndex(byte[] key) {
		Objects.requireNonNull(key, "key is required");

		this.prefix = key[0];
		this.identifier = Arrays.copyOfRange(key, 1, key.length);
	}

	public int getPrefix() {
		return this.prefix;
	}

	public byte[] getIdentifier() {
		return this.identifier;
	}

	public byte[] getKey() {
		return from(this.prefix, this.identifier);
	}
}
