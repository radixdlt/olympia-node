package com.radixdlt.client.atommodel.tokens;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.ParticleIndex;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;

@SerializerId2("TOKENCLASSREFERENCE")
public final class TokenClassReference extends ParticleIndex {
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

	@JsonProperty("symbol")
	@DsonOutput(Output.ALL)
	private String symbol;

	TokenClassReference() {
		// No-arg constructor for serializer
	}

	private TokenClassReference(RadixAddress address, String symbol) {
		super(address);
		Objects.requireNonNull(symbol);

		this.symbol = symbol;
	}

	public static TokenClassReference of(RadixAddress address, String reference) {
		return new TokenClassReference(address, reference);
	}

	public String getSymbol() {
		return symbol;
	}

	public static EUID calcEUID(String isoCode) {
		return RadixHash.of(isoCode.getBytes(CHARSET)).toEUID();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenClassReference)) {
			return false;
		}

		TokenClassReference tokenClassReference = (TokenClassReference) o;
		return this.symbol.equals(tokenClassReference.symbol) && this.getAddress().equals(tokenClassReference.getAddress());
	}

	@Override
	public int hashCode() {
		return toString().hashCode(); //FIXME: quick hack for now
	}

	@Override
	public String toString() {
		return String.format("%s/@%s", this.getAddress().toString(), symbol);
	}
}
