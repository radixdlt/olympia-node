package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;

@SerializerId2("TOKENDEFINITIONPARTICLE")
public class TokenDefinitionParticle extends Particle implements Identifiable, Ownable {
	public enum TokenTransition {
		MINT,
		BURN
	}

	@JsonProperty("address")
	@DsonOutput(Output.ALL)
	private RadixAddress address;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("symbol")
	@DsonOutput(DsonOutput.Output.ALL)
	private String symbol;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("icon")
	@DsonOutput(Output.ALL)
	private byte[] icon;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	private TokenDefinitionParticle() {
		// Empty constructor for serializer
	}

	public TokenDefinitionParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 granularity,
		Map<TokenTransition, TokenPermission> tokenPermissions,
		byte[] icon
	) {
		super();
		this.address = address;
		this.name = name;
		this.symbol = symbol;
		this.description = description;
		this.granularity = granularity;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
		this.icon = icon;
	}

	@Override
	public RadixResourceIdentifer getRRI() {
		return new RadixResourceIdentifer(address, "tokens", this.symbol);
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@Override
	public RadixAddress getAddress() {
		return this.address;
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return getTokenDefinitionReference().getSymbol();
	}

	public String getDescription() {
		return description;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public TokenDefinitionReference getTokenDefinitionReference() {
		return TokenDefinitionReference.of(address, symbol);
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
}
