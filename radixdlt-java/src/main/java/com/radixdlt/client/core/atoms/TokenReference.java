package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TokenReference {

	private static final Charset CHARSET = StandardCharsets.UTF_8;
	public static final int SUB_UNITS = 100000;

	private final AccountReference address;
	private final String iso;

	private TokenReference(AccountReference address, String iso) {
		Objects.requireNonNull(iso);

		this.address = address;
		this.iso = iso;
	}

	public static TokenReference of(AccountReference address, String reference) {
		return new TokenReference(address, reference);
	}

	public String getIso() {
		return iso;
	}

	public static EUID calcEUID(String isoCode) {
		return RadixHash.of(isoCode.getBytes(CHARSET)).toEUID();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenReference)) {
			return false;
		}

		TokenReference tokenReference = (TokenReference) o;
		return this.iso.equals(tokenReference.iso);
	}

	@Override
	public int hashCode() {
		return toString().hashCode(); //FIXME: quick hack for now
	}

	@Override
	public String toString() {
		return String.format("%s/@%s", address.toString(), iso);
	}
}
