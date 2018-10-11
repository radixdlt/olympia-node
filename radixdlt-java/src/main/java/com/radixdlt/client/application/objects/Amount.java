package com.radixdlt.client.application.objects;

import com.radixdlt.client.core.atoms.TokenReference;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Class mainly for formatting amounts in error messages and other English text.
 */
public class Amount {
	public static Amount subUnitsOf(long amountInSubunits, TokenReference tokenReference) {
		return new Amount(tokenReference, amountInSubunits);
	}

	public static Amount of(long amount, TokenReference tokenReference) {
		return new Amount(tokenReference, TokenReference.SUB_UNITS * amount);
	}

	public static Amount of(BigDecimal amount, TokenReference tokenReference) {
		BigDecimal subUnitAmount = amount.multiply(BigDecimal.valueOf(TokenReference.SUB_UNITS)).stripTrailingZeros();
		if (subUnitAmount.scale() > 0) {
			throw new IllegalArgumentException("Amount " + amount + " cannot be used for "
				+ tokenReference + " which has a subunit value of " + TokenReference.SUB_UNITS);
		}

		return new Amount(tokenReference, subUnitAmount.longValueExact());
	}

	private final TokenReference tokenReference;
	private final long amountInSubunits;

	private Amount(TokenReference tokenReference, long amountInSubunits) {
		Objects.requireNonNull(tokenReference);
		if (amountInSubunits < 0) {
			throw new IllegalArgumentException("amount cannot be negative");
		}

		this.tokenReference = tokenReference;
		this.amountInSubunits = amountInSubunits;
	}

	public TokenReference getTokenReference() {
		return tokenReference;
	}

	public long getAmountInSubunits() {
		return amountInSubunits;
	}

	public boolean lte(Amount amount) {
		if (!amount.getTokenReference().equals(this.getTokenReference())) {
			throw new IllegalArgumentException("Only amounts with the same token class can be compared.");
		}

		return this.getAmountInSubunits() <= amount.getAmountInSubunits();
	}

	public boolean gte(Amount amount) {
		if (!amount.getTokenReference().equals(this.getTokenReference())) {
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
			return amount.amountInSubunits == this.amountInSubunits && amount.tokenReference.equals(this.tokenReference);
		}

		return false;
	}

	@Override
	public String toString() {
		return formattedAmount() + " " + tokenReference.getIso();
	}

	private String formattedAmount() {
		long remainder = amountInSubunits % TokenReference.SUB_UNITS;

		if (remainder == 0) {
			// Whole number
			return String.valueOf(amountInSubunits / TokenReference.SUB_UNITS);
		}

		// Decimal format
		return BigDecimal.valueOf(amountInSubunits).divide(BigDecimal.valueOf(TokenReference.SUB_UNITS)).toString();
	}
}
