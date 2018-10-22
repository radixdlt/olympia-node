package com.radixdlt.client.core.atoms.particles;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.radix.common.ID.EUID;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.DsonOutput.Output;
import org.radix.serialization2.SerializerId2;
import org.radix.serialization2.client.Serialize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.TokenRef;
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

	@JsonProperty("uid")
	@DsonOutput(Output.ALL)
	private EUID uid;

	private Spin spin;

	@JsonProperty("addresses")
	@DsonOutput(Output.ALL)
	private List<AccountReference> addresses;

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
		this.addresses = Collections.singletonList(accountReference);
		this.iso = iso;
		this.spin = Spin.UP;
		// FIXME: bad hack
		this.uid = RadixHash.of(Serialize.getInstance().toDson(getTokenRef(), Output.HASH)).toEUID();
		this.name = name;
		this.description = description;
		this.mintPermissions = mintPermissions;
		this.icon = icon;
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

	public TokenRef getTokenRef() {
		return TokenRef.of(addresses.get(0), iso);
	}

	@Override
	public Set<ECPublicKey> getAddresses() {
		return Collections.singleton(addresses.get(0).getKey());
	}

	@Override
	public Spin getSpin() {
		return spin;
	}

	@JsonProperty("spin")
	@DsonOutput(value = {Output.WIRE, Output.API, Output.PERSIST})
	private int getJsonSpin() {
		return this.spin.ordinalValue();
	}

	@JsonProperty("spin")
	private void setJsonSpin(int spin) {
		this.spin = Spin.valueOf(spin);
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
