package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.application.translate.StageActionException;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Objects;

public final class TokenOverMintException extends StageActionException {
	private final RRI tokenDefinitionReference;
	private final BigDecimal maxAmount;
	private final BigDecimal currentAmount;
	private final BigDecimal requestedAmount;

	public TokenOverMintException(
		RRI tokenDefinitionReference,
		BigDecimal maxAmount,
		BigDecimal currentAmount,
		BigDecimal requestedAmount
	) {
		super("Mint amount of " + requestedAmount + " would overflow maximum of " + maxAmount + ". Current is " + currentAmount + ".");
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.maxAmount = Objects.requireNonNull(maxAmount);
		this.currentAmount = Objects.requireNonNull(currentAmount);
		this.requestedAmount = Objects.requireNonNull(requestedAmount);
	}

	public BigDecimal getMaxAmount() {
		return maxAmount;
	}

	public BigDecimal getCurrentAmount() {
		return currentAmount;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof TokenOverMintException)) {
			return false;
		}

		TokenOverMintException o = (TokenOverMintException) obj;
		return this.tokenDefinitionReference.equals(o.tokenDefinitionReference)
			&& this.maxAmount.compareTo(o.maxAmount) == 0
			&& this.currentAmount.compareTo(o.currentAmount) == 0
			&& this.requestedAmount.compareTo(o.requestedAmount) == 0;
	}

	@Override
	public int hashCode() {
		return this.getMessage().hashCode();
	}
}
