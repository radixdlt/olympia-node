package com.radixdlt.client.core.atoms.particles;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.radixdlt.client.core.atoms.particles.quarks.AddressableQuark;
import com.radixdlt.client.core.atoms.particles.quarks.NonFungibleQuark;
import com.radixdlt.client.core.atoms.particles.quarks.OwnableQuark;
import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.crypto.ECPublicKey;

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

	TokenParticle() {
		// Empty constructor for serializer
	}

	public TokenParticle(
			AccountReference accountReference,
			String name,
			String iso,
			String description,
			MintPermissions mintPermissions,
			byte[] icon
	) {
		super(Spin.UP, new NonFungibleQuark(RadixHash.of(Serialize.getInstance().toDson(getTokenClassReference(accountReference, iso), Output.HASH)).toEUID()),
				new AddressableQuark(accountReference), new OwnableQuark(accountReference));
		
		this.iso = iso;
		this.name = name;
		this.description = description;
		this.mintPermissions = mintPermissions;
		this.icon = icon;
	}

	private static TokenClassReference getTokenClassReference(AccountReference accountReference, String iso) {
		return TokenClassReference.of(accountReference, iso);
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
		return Collections.singleton(getQuarkOrError(AddressableQuark.class).getAddresses().get(0).getKey());
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
