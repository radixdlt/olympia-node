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

	private Map<Class<? extends Particle>, TokenPermission> tokenPermissions;

	private TokenDefinitionParticle() {
		// Empty constructor for serializer
	}

	public TokenDefinitionParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 granularity,
		Map<Class<? extends Particle>, TokenPermission> tokenPermissions,
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

	public Map<Class<? extends Particle>, TokenPermission> getTokenPermissions() {
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
			.collect(Collectors.toMap(e -> tokenDefinitionToVerb(e.getKey()), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> verbToTokenClass(e.getKey()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	private static final BiMap<Class<? extends Particle>, String> TOKENS_CLASS_TO_VERB = ImmutableBiMap.of(
		MintedTokensParticle.class, "mint",
		BurnedTokensParticle.class, "burn"
	);
	private static final BiMap<String, Class<? extends Particle>> VERB_TO_TOKEN_CLASS = TOKENS_CLASS_TO_VERB.inverse();

	public static String tokenDefinitionToVerb(Class<? extends Particle> particleClass) {
		return TOKENS_CLASS_TO_VERB.get(particleClass);
	}

	public static Class<? extends Particle> verbToTokenClass(String verb) {
		return VERB_TO_TOKEN_CLASS.get(verb);
	}
}
