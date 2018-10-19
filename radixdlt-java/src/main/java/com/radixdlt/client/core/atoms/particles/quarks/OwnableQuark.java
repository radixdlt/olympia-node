package com.radixdlt.client.core.atoms.particles.quarks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.atoms.AccountReference;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.SerializerId2;

import java.util.Objects;

/**
 * A quark that protects a particle from being spun DOWN unless it was signed by the owner
 */
@SerializerId2("OWNABLEQUARK")
public final class OwnableQuark extends Quark {
	@JsonProperty("account_reference")
	@DsonOutput(DsonOutput.Output.ALL)
	private AccountReference accountReference;

	private OwnableQuark() {
	}

	public OwnableQuark(AccountReference accountReference) {
		this.accountReference = Objects.requireNonNull(accountReference);
	}

	public AccountReference getAccountReference() {
		return this.accountReference;
	}
}
