package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Token {

	private static final Charset CHARSET = StandardCharsets.UTF_8;
	public static final int SUB_UNITS = 100000;

	private final String iso;

	private Token(String iso) {
		Objects.requireNonNull(iso);

		this.iso = iso;
	}

	public static Token of(String reference) {
		return new Token(reference);
	}

	public String getIso() {
		return iso;
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
		return String.format("%s[%s]", getClass().getSimpleName(), iso);
	}
}
