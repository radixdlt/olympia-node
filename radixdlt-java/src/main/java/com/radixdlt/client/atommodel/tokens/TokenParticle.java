package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.quarks.AccountableQuark;
import com.radixdlt.client.atommodel.quarks.NonFungibleQuark;
import com.radixdlt.client.atommodel.quarks.OwnableQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;

import java.util.Collections;
import java.util.Set;

@SerializerId2("TOKENCLASSPARTICLE")
public class TokenParticle extends Particle {
	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

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
		Map<FungibleType, TokenPermission> tokenPermissions,
		byte[] icon
	) {
		super(
			new NonFungibleQuark(TokenClassReference.of(address, symbol)),
			new AccountableQuark(address),
			new OwnableQuark(address.getPublicKey())
		);
		this.name = name;
		this.description = description;
		this.tokenPermissions = Collections.unmodifiableMap(new EnumMap<>(tokenPermissions));
		this.icon = icon;
	}

	public Map<FungibleType, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return getTokenClassReference().getSymbol();
	}

	public String getDescription() {
		return description;
	}

	public TokenClassReference getTokenClassReference() {
		return (TokenClassReference) this.getQuarkOrError(NonFungibleQuark.class).getIndex();
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
