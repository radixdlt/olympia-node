package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class TokenRef {
	private static final Charset CHARSET = StandardCharsets.UTF_8;

	private static final int TOKEN_SCALE = 5;
	public static final int SUB_UNITS = 100000;
	private static final BigDecimal SUB_UNITS_BIG_DECIMAL = new BigDecimal(SUB_UNITS);

	public static int getTokenScale() {
		return TOKEN_SCALE;
	}

	public static BigDecimal getSubUnits() {
		return SUB_UNITS_BIG_DECIMAL;
	}

	public static BigDecimal subUnitsToDecimal(long subUnits) {
		return BigDecimal.valueOf(subUnits, TOKEN_SCALE);
	}

	private final AccountReference address;
	private final String iso;

	private TokenRef(AccountReference address, String iso) {
		Objects.requireNonNull(iso);

		this.address = address;
		this.iso = iso;
	}

	public static TokenRef of(AccountReference address, String reference) {
		return new TokenRef(address, reference);
	}

	public AccountReference getAddress() {
		return address;
	}

	public String getIso() {
		return iso;
	}

	public static EUID calcEUID(String isoCode) {
		return RadixHash.of(isoCode.getBytes(CHARSET)).toEUID();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenRef)) {
			return false;
		}

		TokenRef tokenRef = (TokenRef) o;
		return this.iso.equals(tokenRef.iso) && this.address.equals(tokenRef.address);
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
