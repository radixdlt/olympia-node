package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 * A mutable version of {@link Fungible} for fast internal processing
 */
public class MutableFungible {
	private final Fungible fungible;
	private UInt256 amount;

	public MutableFungible(Fungible fungible) {
		this(fungible, UInt256.ZERO);
	}

	public MutableFungible(Fungible fungible, UInt256 amount) {
		this.fungible = Objects.requireNonNull(fungible, "fungible is required");
		this.amount = Objects.requireNonNull(amount, "amount is required");
	}

	public Fungible asFungible() {
		return fungible.withAmount(amount);
	}

	public void setAmount(UInt256 amount) {
		this.amount = amount;
	}

	public void subtract(UInt256 other) {
		this.amount = this.amount.subtract(other);
	}

	public void add(UInt256 other) {
		this.amount = this.amount.add(other);
	}

	public void divide(UInt256 divisor) {
		this.amount = this.amount.divide(divisor);
	}

	public UInt256 getAmount() {
		return amount;
	}

	public Fungible getFungible() {
		return fungible;
	}
}
