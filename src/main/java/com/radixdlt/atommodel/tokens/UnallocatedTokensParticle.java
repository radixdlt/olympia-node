package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *  A particle which represents an amount of unallocated tokens which can be minted.
 */
@SerializerId2("radix.particles.unallocated_tokens")
public class UnallocatedTokensParticle extends Particle {
	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	private UnallocatedTokensParticle() {
		super();
	}

	public UnallocatedTokensParticle(
		UInt256 amount,
		UInt256 granularity,
		RRI tokenDefinitionReference,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super(tokenDefinitionReference.getAddress().getUID());

		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.nonce = System.nanoTime();
		this.amount = Objects.requireNonNull(amount);
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public RadixAddress getAddress() {
		return this.tokenDefinitionReference.getAddress();
	}

	public RRI getTokDefRef() {
		return this.tokenDefinitionReference;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	public TokenPermission getTokenPermission(TokenTransition transition) {
		TokenPermission tokenPermission = tokenPermissions.get(transition);
		if (tokenPermission != null) {
			return tokenPermission;
		}

		throw new IllegalArgumentException("No token permission set for " + transition + " in " + tokenPermissions);
	}

	@JsonProperty("permissions")
	@DsonOutput(Output.ALL)
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(
					Collectors.toMap(
						e -> TokenTransition.valueOf(e.getKey().toUpperCase()),
						e -> TokenPermission.valueOf(e.getValue().toUpperCase())
					)
				);
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
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

	public UInt256 getAmount() {
		return this.amount;
	}

	public long getNonce() {
		return this.nonce;
	}
}
