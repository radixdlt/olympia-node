package com.radixdlt.client.application.objects;

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
		return new Amount(token, token.getSubUnits() * amount);
	}

	public static Amount of(BigDecimal amount, Token token) {
		BigDecimal subUnitAmount = amount.multiply(BigDecimal.valueOf(token.getSubUnits())).stripTrailingZeros();
		if (subUnitAmount.scale() > 0) {
			throw new IllegalArgumentException("Amount " + amount + " cannot be used for "
				+ token + " which has a subunit value of " + token.getSubUnits());
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
		long remainder = amountInSubunits % token.getSubUnits();

		if (remainder == 0) {
			// Whole number
			return String.valueOf(amountInSubunits / token.getSubUnits());
		}

		if (isPowerOfTen(token.getSubUnits())) {
			// Decimal format
			return BigDecimal.valueOf(amountInSubunits).divide(BigDecimal.valueOf(token.getSubUnits())).toString();
		}

		// Fraction format
		long quotient = amountInSubunits / token.getSubUnits();
		String fraction = remainder + "/" + token.getSubUnits();
		if (quotient == 0) {
			return fraction;
		}
		return quotient + " and " + fraction;
	}

	private boolean isPowerOfTen(int value) {
		while (value > 9 && value % 10 == 0) {
			value /= 10;
		}
		return value == 1;
	}
}
