package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atomos.RRI;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atoms.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *  A particle which represents an amount of transferrable fungible tokens
 *  owned by some key owner and stored in an account.
 */
@SerializerId2("radix.particles.transferrable_tokens")
public final class TransferrableTokensParticle extends Particle {
	@JsonProperty("address")
	@DsonOutput(DsonOutput.Output.ALL)
	private RadixAddress address;

	@JsonProperty("tokenDefinitionReference")
	@DsonOutput(DsonOutput.Output.ALL)
	private RRI tokenDefinitionReference;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 granularity;

	@JsonProperty("planck")
	@DsonOutput(DsonOutput.Output.ALL)
	private long planck;

	@JsonProperty("nonce")
	@DsonOutput(DsonOutput.Output.ALL)
	private long nonce;

	@JsonProperty("amount")
	@DsonOutput(DsonOutput.Output.ALL)
	private UInt256 amount;

	// FIXME: Temporary, will remove once atom dependency is implemented
	private Map<TokenTransition, TokenPermission> tokenPermissions;

	public TransferrableTokensParticle() {
		super();
	}

	public TransferrableTokensParticle(
		RadixAddress address,
		UInt256 amount,
		UInt256 granularity,
		RRI tokenDefinitionReference,
		long planck,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super(address.getUID());

		this.address = Objects.requireNonNull(address);
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenDefinitionReference = Objects.requireNonNull(tokenDefinitionReference);
		this.planck = planck;
		this.nonce = System.nanoTime();
		this.amount = Objects.requireNonNull(amount);
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
	}

	public RadixAddress getAddress() {
		return this.address;
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
		return tokenPermissions.get(transition);
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
		return String.format("%s[%s:%s:%s:%s:%s:%s]",
			getClass().getSimpleName(),
			String.valueOf(tokenDefinitionReference),
			String.valueOf(amount),
			String.valueOf(granularity),
			String.valueOf(address),
			planck,
			nonce);
	}

	public UInt256 getAmount() {
		return this.amount;
	}

	public long getPlanck() {
		return this.planck;
	}

	public long getNonce() {
		return this.nonce;
	}
}
