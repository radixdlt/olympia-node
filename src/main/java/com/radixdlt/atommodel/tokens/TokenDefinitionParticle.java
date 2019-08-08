package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
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

@SerializerId2("radix.particles.token_definition")
public final class TokenDefinitionParticle extends Particle {
	/**
	 * Power of 10 number of subunits to be used by every token.
	 * Follows EIP-777 model.
	 */
	public static final int SUB_UNITS_POW_10 = 18;

	/**
	 * Implicit number of subunits to be used by every token. Follows EIP-777 model.
	 */
	public static final UInt256 SUB_UNITS = UInt256.TEN.pow(SUB_UNITS_POW_10);

	public static final int MIN_SYMBOL_LENGTH = 1;
	public static final int MAX_SYMBOL_LENGTH = 14;
	public static final String VALID_SYMBOL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	public static final int MAX_DESCRIPTION_LENGTH = 200;
	public static final int MAX_ICON_DIMENSION = 32;

	public enum TokenTransition {
		MINT,
		BURN
	}

	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private String	name;

	@JsonProperty("symbol")
	@DsonOutput(DsonOutput.Output.ALL)
	private String symbol;

	@JsonProperty("description")
	@DsonOutput(DsonOutput.Output.ALL)
	private String	description;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("iconUrl")
	@DsonOutput(DsonOutput.Output.ALL)
	private String iconUrl;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	private TokenDefinitionParticle() {
		super();
	}

	public TokenDefinitionParticle(
		RadixAddress address,
		String symbol,
		String name,
		String description,
		UInt256 granularity,
		String iconUrl,
		Map<TokenTransition, TokenPermission> tokenPermissions
	) {
		super(address.getUID());

		this.address = address;
		this.name = name;
		this.symbol = symbol;
		this.description = description;
		this.granularity = Objects.requireNonNull(granularity);
		this.iconUrl = iconUrl;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);

		if (this.tokenPermissions.keySet().size() != TokenTransition.values().length) {
		    throw new IllegalArgumentException("tokenPermissions must be set for all token instance types.");
		}
	}

	public RRI getRRI() {
		return RRI.of(getAddress(), getSymbol());
	}

	public RadixAddress getAddress() {
		return this.address;
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

	public String getSymbol() {
		return this.symbol;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public String getIconUrl() {
		return this.iconUrl;
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
		String tokenPermissionsStr = (tokenPermissions == null)
			? "null"
			: tokenPermissions.entrySet().stream()
				.map(e -> String.format("%s:%s", e.getKey().toString().toLowerCase(), e.getValue().toString().toLowerCase()))
				.collect(Collectors.joining(","));
		return String.format("%s[(%s:%s:%s), (am%s), (%s), %s]", getClass().getSimpleName(),
			String.valueOf(name), String.valueOf(symbol), String.valueOf(granularity),
			String.valueOf(description), tokenPermissionsStr, String.valueOf(address));
	}
}
