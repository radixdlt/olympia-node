package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.core.atoms.particles.RRI;

@SerializerId2("radix.particles.mutable_supply_token_definition")
public class MutableSupplyTokenDefinitionParticle extends Particle implements Identifiable, Ownable {
	public enum TokenTransition {
		MINT,
		BURN
	}

	@JsonProperty("rri")
	@DsonOutput(Output.ALL)
	private RRI rri;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("iconUrl")
	@DsonOutput(Output.ALL)
	private String iconUrl;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	MutableSupplyTokenDefinitionParticle() {
		// Empty constructor for serializer
	}

	public MutableSupplyTokenDefinitionParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 granularity,
		Map<TokenTransition, TokenPermission> tokenPermissions,
		String iconUrl
	) {
		this(RRI.of(address, symbol), name, description, granularity, tokenPermissions, iconUrl);
	}

	public MutableSupplyTokenDefinitionParticle(
		RRI rri,
		String name,
		String description,
		UInt256 granularity,
		Map<TokenTransition, TokenPermission> tokenPermissions,
		String iconUrl
	) {
		super(rri.getAddress().getUID());
		this.rri = rri;
		this.name = name;
		this.description = description;
		this.granularity = granularity;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
		this.iconUrl = iconUrl;
	}

	@Override
	public RRI getRRI() {
		return this.rri;
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@Override
	public RadixAddress getAddress() {
		return this.rri.getAddress();
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return this.rri.getName();
	}

	public String getDescription() {
		return description;
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
				.collect(Collectors.toMap(
					e -> TokenTransition.valueOf(e.getKey().toUpperCase()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public String toString() {
		String tokenPermissionsStr = (tokenPermissions == null)
			? "null"
			: tokenPermissions.entrySet().stream().map(e -> String.format("%s:%s", e.getKey().toString().toLowerCase(),
				e.getValue().toString().toLowerCase())).collect(Collectors.joining(","));
		return String.format("%s[%s (%s:%s), (%s:%s)]", getClass().getSimpleName(),
			String.valueOf(this.rri), name, description,
			String.valueOf(granularity), tokenPermissionsStr);
	}
}
