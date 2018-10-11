package com.radixdlt.client.application.objects;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.RadixHash;

public final class Token {

	private static final Charset CHARSET = StandardCharsets.UTF_8;
	public static final int SUB_UNITS = 100000;
	/**
	 * Radix Token asset. TODO: Read from universe file. Hardcode for now.
	 */
	public static final Token TEST = new Token("XRD");
	public static final Token POW = new Token("POW");

	private final String iso;
	private final EUID id;

	public Token(String iso) {
		Objects.requireNonNull(iso);

		this.iso = iso;
		this.id = calcEUID(iso);
	}

	public static Token of(String reference) {
		return new Token(reference);
	}

	public String getIso() {
		return iso;
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
		return String.format("%s[%s/%s]", getClass().getSimpleName(), iso, id);
	}
}
