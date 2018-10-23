package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

/**
 * A quark that makes a particle fungible: can be cut up into pieces and put back together.
 */
@SerializerId2("FUNGIBLEQUARK")
public final class FungibleQuark extends Quark {
	public enum FungibleType {
		MINTED, AMOUNT
	}

	@JsonProperty("planck")
	@DsonOutput(DsonOutput.Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private long amount;

	private FungibleType type;

	private FungibleQuark() {
	}

	public FungibleQuark(long amount, long planck, FungibleType type) {
		this(amount, planck, System.nanoTime(), type);
	}

	public FungibleQuark(long amount, long planck, long nonce, FungibleType type) {
		if (amount < 1) {
			throw new IllegalArgumentException("Amount is zero or negative");
		}

		this.nonce = nonce;
		this.type = type;
		this.planck = planck;
		this.amount = amount;
	}

	public long getAmount() {
		return this.amount;
	}

	public long getPlanck() {
		return this.planck;
	}

	public FungibleType getType() {
		return this.type;
	}

	public long getNonce() {
		return nonce;
	}


	@JsonProperty("type")
	@DsonOutput(DsonOutput.Output.ALL)
	private String getJsonType() {
		return this.type == null ? null : this.type.name().toLowerCase();
	}

	@JsonProperty("type")
	private void setJsonType(String type) {
		this.type = type == null ? null : FungibleType.valueOf(type.toUpperCase());
	}
}
