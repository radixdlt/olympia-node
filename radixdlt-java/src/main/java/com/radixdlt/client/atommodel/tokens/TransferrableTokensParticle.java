package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Accountable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import java.util.Map;
import java.util.stream.Collectors;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 *  A particle which represents an amount of consumable and consuming, tranferable fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("TRANSFERRABLETOKENSPARTICLE")
public final class TransferrableTokensParticle extends Particle implements Accountable, Ownable {
	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(Output.ALL)
	private RadixResourceIdentifer tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("planck")
	@DsonOutput(Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(Output.ALL)
	private UInt256 amount;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	protected TransferrableTokensParticle() {
	}

	public TransferrableTokensParticle(
		UInt256 amount,
		UInt256 granularity,
		RadixAddress address,
		long nonce,
		TokenDefinitionReference tokenDefinitionReference,
		long planck,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super();

		// Redundant null check added for completeness
		Objects.requireNonNull(amount, "amount is required");
		if (amount.isZero()) {
			throw new IllegalArgumentException("Amount is zero");
		}

		this.address = address;
		this.tokenDefinitionReference = new RadixResourceIdentifer(
			tokenDefinitionReference.getAddress(), "tokens", tokenDefinitionReference.getSymbol());
		this.granularity = granularity;
		this.planck = planck;
		this.nonce = nonce;
		this.amount = amount;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@JsonProperty("permissions")
	@DsonOutput(value = {Output.ALL})
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> TokenTransition.valueOf(e.getKey().toUpperCase()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public Set<RadixAddress> getAddresses() {
		return Collections.singleton(this.address);
	}

	@Override
	public RadixAddress getAddress() {
		return this.address;
	}

	public long getNonce() {
		return this.nonce;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return TokenDefinitionReference.of(tokenDefinitionReference.getAddress(), tokenDefinitionReference.getUnique());
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenDefinitionReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			String.valueOf(address),
			planck,
			nonce);
	}
}
