package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Fungible;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("UNALLOCATEDTOKENSPARTICLE")
public class UnallocatedTokensParticle extends Particle implements Accountable, Ownable, Fungible {

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixResourceIdentifer tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	private Map<Class<? extends Particle>, TokenPermission> tokenPermissions;

	public UnallocatedTokensParticle() {
		super();
	}

	public UnallocatedTokensParticle(
		UInt256 amount,
		UInt256 granularity,
		long nonce,
		TokenDefinitionReference tokenDefinitionReference,
		Map<Class<? extends Particle>, TokenPermission> tokenPermissions
	) {
		super();

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.tokenDefinitionReference = new RadixResourceIdentifer(
			tokenDefinitionReference.getAddress(), "tokens", tokenDefinitionReference.getSymbol());
		this.granularity = granularity;
		this.nonce = nonce;
		this.amount = amount;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public Map<Class<? extends Particle>, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@JsonProperty("permissions")
	@DsonOutput(value = {Output.ALL})
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> TokenDefinitionParticle.tokenDefinitionToVerb(e.getKey()), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> TokenDefinitionParticle.verbToTokenClass(e.getKey()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(this.tokenDefinitionReference.getAddress());
	}

	public RadixAddress getAddress() {
		return this.tokenDefinitionReference.getAddress();
	}

	public RadixResourceIdentifer getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return TokenDefinitionReference.of(tokenDefinitionReference.getAddress(), tokenDefinitionReference.getUnique());
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenDefinitionReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			nonce);
	}

	@Override
	public UInt256 getAmount() {
		return this.amount;
	}

	@Override
	public long getNonce() {
		return this.nonce;
	}
}
