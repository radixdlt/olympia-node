package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.core.atoms.RadixHash;
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
import org.radix.serialization2.client.Serialize;

import java.util.Collections;
import java.util.Set;

@SerializerId2("TOKENCLASSPARTICLE")
public class TokenParticle extends Particle {
	@JsonProperty("iso")
	@DsonOutput(Output.ALL)
	private String iso;

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
			String iso,
			String description,
			Map<FungibleType, TokenPermission> tokenPermissions,
			byte[] icon
	) {
		super(new NonFungibleQuark(RadixHash.of(Serialize.getInstance()
						.toDson(getTokenClassReference(address, iso), Output.HASH)).toEUID()),
				new AccountableQuark(address), new OwnableQuark(address.getPublicKey()));
		this.iso = iso;
		this.name = name;
		this.description = description;
		this.tokenPermissions = Collections.unmodifiableMap(new EnumMap<>(tokenPermissions));
		this.icon = icon;
	}

	private static TokenClassReference getTokenClassReference(RadixAddress address, String iso) {
		return TokenClassReference.of(address, iso);
	}

	public String getName() {
		return name;
	}

	public String getIso() {
		return iso;
	}

	public String getDescription() {
		return description;
	}

	public TokenClassReference getTokenClassReference() {
		return TokenClassReference.of(getQuarkOrError(AccountableQuark.class).getAddresses().get(0), iso);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.singleton(getQuarkOrError(AccountableQuark.class).getAddresses().get(0).getPublicKey());
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
