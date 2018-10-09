package com.radixdlt.client.application.objects;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.RadixHash;

public final class Token {

	private static final Charset CHARSET = StandardCharsets.UTF_8;
	/**
	 * Radix Token asset. TODO: Read from universe file. Hardcode for now.
	 */
	public static final Token TEST = new Token("XRD", 100000);
	public static final Token POW = new Token("POW",       1);

	private final String iso;
	private final int subUnits;
	private final EUID id;

	private Token(String iso, int subUnits, EUID id) {
		Objects.requireNonNull(iso);
		Objects.requireNonNull(id);

		if (subUnits == 0) {
			throw new IllegalArgumentException("Integer assets should have subUnits set to 1 for mathematical reasons");
		}

		this.iso = iso;
		this.subUnits = subUnits;
		this.id = id;
	}

	public Token(String iso, int subUnits) {
		this(iso, subUnits, calcEUID(iso));
	}

	public String getIso() {
		return iso;
	}

	public int getSubUnits() {
		return subUnits;
	}

	public EUID getId() {
		return id;
	}

	public static EUID calcEUID(String isoCode) {
		return RadixHash.of(isoCode.getBytes(CHARSET)).toEUID();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Token)) {
			return false;
		}

		Token token = (Token) o;
		return this.iso.equals(token.iso);
	}

	@Override
	public int hashCode() {
		return iso.hashCode();
	}

	@Override
	public String toString() {
		return String.format("%s[%s/%s/%s]", getClass().getSimpleName(), iso, subUnits, id);
	}
}
