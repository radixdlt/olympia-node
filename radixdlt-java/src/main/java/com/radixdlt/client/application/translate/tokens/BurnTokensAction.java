package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import java.util.Objects;

public class BurnTokensAction implements Action {
	private final RRI rri;
	private final RadixAddress address;
	private final BigDecimal amount;

	private BurnTokensAction(RRI rri, RadixAddress address, BigDecimal amount) {
		this.rri = Objects.requireNonNull(rri);
		this.address = Objects.requireNonNull(address);
		this.amount = Objects.requireNonNull(amount);
	}

	public static BurnTokensAction create(
		RRI rri,
		RadixAddress address,
		BigDecimal amount
	) {
		return new BurnTokensAction(rri, address, amount);
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
		return "BURN TOKEN " + amount + " " + rri;
	}
}
