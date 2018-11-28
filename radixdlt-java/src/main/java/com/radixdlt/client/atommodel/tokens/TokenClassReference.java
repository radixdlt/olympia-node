package com.radixdlt.client.atommodel.tokens;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;
import org.radix.utils.UInt256s;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.ParticleIndex;

@SerializerId2("TOKENCLASSREFERENCE")
public final class TokenClassReference extends ParticleIndex {
	private static final Charset CHARSET = StandardCharsets.UTF_8;

	public static final int SUB_UNITS_POW_10 = 18;
	public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);
	private static final BigDecimal SUB_UNITS_BIG_DECIMAL = UInt256s.toBigDecimal(SUB_UNITS);

	public static int getTokenScale() {
		return SUB_UNITS_POW_10;
	}

	public static BigDecimal getSubUnits() {
		return SUB_UNITS_BIG_DECIMAL;
	}

	public static BigDecimal subUnitsToDecimal(UInt256 subUnits) {
		return subUnitsToDecimal(UInt256s.toBigInteger(subUnits));
	}

	public static BigDecimal subUnitsToDecimal(BigInteger subUnits) {
		return new BigDecimal(subUnits, SUB_UNITS_POW_10);
	}

	public static BigDecimal subUnitsToDecimal(long subUnits) {
		return BigDecimal.valueOf(subUnits, SUB_UNITS_POW_10);
	}

	public static UInt256 fromUnits(long units) {
		if (units <= 0) {
			throw new IllegalArgumentException("units must be > 0: " + units);
		}
		// 10^18 is approximately 60 bits, so a positive long (63 bits) cannot overflow here
		return UInt256.from(units).multiply(SUB_UNITS);
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
