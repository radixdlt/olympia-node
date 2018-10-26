package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.atommodel.quarks.AddressableQuark;
import com.radixdlt.client.atommodel.quarks.NonFungibleQuark;
import com.radixdlt.client.atommodel.quarks.OwnableQuark;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import java.util.Collections;
import java.util.Set;

@SerializerId2("TOKENCLASSPARTICLE")
public class TokenParticle extends Particle {
	public enum MintPermissions {
		GENESIS_ONLY,
		SAME_ATOM_ONLY,
		POW
	}

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

	private MintPermissions mintPermissions;

	private TokenParticle() {
		// Empty constructor for serializer
	}

	public TokenParticle(
			RadixAddress address,
			String name,
			String iso,
			String description,
			MintPermissions mintPermissions,
			byte[] icon
	) {
		super(new NonFungibleQuark(RadixHash.of(Serialize.getInstance()
						.toDson(getTokenClassReference(address, iso), Output.HASH)).toEUID()),
				new AddressableQuark(address), new OwnableQuark(address.getPublicKey()));
		this.iso = iso;
		this.name = name;
		this.description = description;
		this.mintPermissions = mintPermissions;
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
		return TokenClassReference.of(getQuarkOrError(AddressableQuark.class).getAddresses().get(0), iso);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.singleton(getQuarkOrError(AddressableQuark.class).getAddresses().get(0).getPublicKey());
	}

	@JsonProperty("mint_permissions")
	@DsonOutput(Output.ALL)
	private String getJsonMintPermissions() {
		return mintPermissions == null ? null : mintPermissions.name().toLowerCase();
	}

	@JsonProperty("mint_permissions")
	private void setJsonMintPermissions(String mintPermissions) {
		this.mintPermissions = mintPermissions == null ? null : MintPermissions.valueOf(mintPermissions.toUpperCase());
	}
}
