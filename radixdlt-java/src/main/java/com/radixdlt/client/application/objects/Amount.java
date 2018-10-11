package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.atoms.Token;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Class mainly for formatting amounts in error messages and other English text.
 */
public class Amount {
	public static Amount subUnitsOf(long amountInSubunits, Token token) {
		return new Amount(token, amountInSubunits);
	}

	public static Amount of(long amount, Token token) {
		return new Amount(token, Token.SUB_UNITS * amount);
	}

	public static Amount of(BigDecimal amount, Token token) {
		BigDecimal subUnitAmount = amount.multiply(BigDecimal.valueOf(Token.SUB_UNITS)).stripTrailingZeros();
		if (subUnitAmount.scale() > 0) {
			throw new IllegalArgumentException("Amount " + amount + " cannot be used for "
				+ token + " which has a subunit value of " + Token.SUB_UNITS);
		}

		return new Amount(token, subUnitAmount.longValueExact());
	}

	private final Token token;
	private final long amountInSubunits;

	private Amount(Token token, long amountInSubunits) {
		Objects.requireNonNull(token);
		if (amountInSubunits < 0) {
			throw new IllegalArgumentException("amount cannot be negative");
		}

		this.token = token;
		this.amountInSubunits = amountInSubunits;
	}

	public Token getToken() {
		return token;
	}

	public long getAmountInSubunits() {
		return amountInSubunits;
	}

	public boolean lte(Amount amount) {
		if (!amount.getToken().equals(this.getToken())) {
			throw new IllegalArgumentException("Only amounts with the same token class can be compared.");
		}

		return this.getAmountInSubunits() <= amount.getAmountInSubunits();
	}

	public boolean gte(Amount amount) {
		if (!amount.getToken().equals(this.getToken())) {
			throw new IllegalArgumentException("Only amounts with the same token class can be compared.");
		}

		return this.getAmountInSubunits() >= amount.getAmountInSubunits();
	}

	@Override
	public int hashCode() {
		// Hackish but good for now
		return this.toString().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Amount) {
			Amount amount = (Amount) o;
			return amount.amountInSubunits == this.amountInSubunits && amount.token.equals(this.token);
		}

		return false;
	}

	@Override
	public String toString() {
		return formattedAmount() + " " + token.getIso();
	}

	private String formattedAmount() {
		long remainder = amountInSubunits % Token.SUB_UNITS;

		if (remainder == 0) {
			// Whole number
			return String.valueOf(amountInSubunits / Token.SUB_UNITS);
		}

		// Decimal format
		return BigDecimal.valueOf(amountInSubunits).divide(BigDecimal.valueOf(Token.SUB_UNITS)).toString();
	}
}
