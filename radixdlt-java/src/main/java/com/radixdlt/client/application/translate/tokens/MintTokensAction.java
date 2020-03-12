package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public final class MintTokensAction implements Action {
	private final RRI rri;
	private final RadixAddress address;
	private final BigDecimal amount;

	private MintTokensAction(RRI rri, RadixAddress address, BigDecimal amount) {
		this.rri = Objects.requireNonNull(rri);
		this.address = Objects.requireNonNull(address);
		this.amount = Objects.requireNonNull(amount);
	}

	public static MintTokensAction create(
		RRI tokenDefRef,
		RadixAddress address,
		BigDecimal amount
	) {
		return new MintTokensAction(tokenDefRef, address, amount);
	}

	public RadixAddress getAddress() {
		return address;
	}

	public RRI getRRI() {
		return rri;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	@Override
	public String toString() {
		return "MINT TOKEN " + amount + " " + rri;
	}
}
