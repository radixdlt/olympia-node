package com.radixdlt.client.atommodel.tokens;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.utils.UInt256;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.Identifiable;
import com.radixdlt.client.atommodel.Ownable;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RadixResourceIdentifer;
import com.radixdlt.client.core.crypto.ECPublicKey;

@SerializerId2("TOKENCLASSPARTICLE")
public class TokenParticle extends Particle implements Identifiable, Ownable {
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

	private Map<FungibleType, TokenPermission> tokenPermissions;

	private TokenParticle() {
		// Empty constructor for serializer
	}

	public TokenParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 granularity,
		Map<FungibleType, TokenPermission> tokenPermissions,
		byte[] icon
	) {
		super();
		this.address = address;
		this.name = name;
		this.symbol = symbol;
		this.description = description;
		this.granularity = granularity;
		this.tokenPermissions = Collections.unmodifiableMap(new EnumMap<>(tokenPermissions));
		this.icon = icon;
	}

	@Override
	public RadixResourceIdentifer getRRI() {
		return new RadixResourceIdentifer(address, "tokenclasses", this.symbol);
	}

	public Map<FungibleType, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	@Override
	public ECPublicKey getOwner() {
		return this.address.getPublicKey();
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return getTokenTypeReference().getSymbol();
	}

	public String getDescription() {
		return description;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public TokenTypeReference getTokenTypeReference() {
		return TokenTypeReference.of(address, symbol);
	}

	@JsonProperty("permissions")
	@DsonOutput(value = {Output.ALL})
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().getVerbName(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> FungibleType.fromVerbName(e.getKey()), e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}
}
