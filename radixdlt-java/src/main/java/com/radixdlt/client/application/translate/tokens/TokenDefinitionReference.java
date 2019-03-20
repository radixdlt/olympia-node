package com.radixdlt.client.application.translate.tokens;

import java.util.Objects;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

public final class TokenDefinitionReference {
	private final RadixAddress address;
	private final String symbol;

	private TokenDefinitionReference(RadixAddress address, String symbol) {
		Objects.requireNonNull(symbol);
		Objects.requireNonNull(address);
		this.address = address;
		this.symbol = symbol;
	}

	public static TokenDefinitionReference of(RadixAddress address, String symbol) {
		return new TokenDefinitionReference(address, symbol);
	}

	public String getSymbol() {
		return this.symbol;
	}

	public RadixAddress getAddress() {
		return address;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenDefinitionReference)) {
			return false;
		}

		TokenDefinitionReference tokenDefinitionReference = (TokenDefinitionReference) o;
		return this.getSymbol().equals(tokenDefinitionReference.getSymbol())
			&& this.getAddress().equals(tokenDefinitionReference.getAddress());
	}

	@Override
	public int hashCode() {
		return toString().hashCode(); //FIXME: quick hack for now
	}

	@Override
	public String toString() {
		return String.format("%s/tokens/@%s", this.getAddress().toString(), this.getSymbol());
	}
}
