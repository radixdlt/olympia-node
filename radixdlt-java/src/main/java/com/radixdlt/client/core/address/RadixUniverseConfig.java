package com.radixdlt.client.core.address;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.serialization.Dson;
import com.radixdlt.client.core.serialization.RadixJson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import org.bouncycastle.util.encoders.Base64;

public class RadixUniverseConfig {

	private final long magic;
	private final long port;
	private final String name;
	private final String description;
	private final RadixUniverseType type;
	private final long timestamp;
	private final ECPublicKey creator;
	private final List<Atom> genesis;

	public static RadixUniverseConfig fromDsonBase64(String dsonBase64) {
		JsonElement universeJson = Dson.getInstance().parse(Base64.decode(dsonBase64));
		System.out.println(universeJson);
		return RadixJson.getGson().fromJson(universeJson, RadixUniverseConfig.class);
	}

	public static RadixUniverseConfig fromInputStream(InputStream inputStream) {
		return RadixJson.getGson().fromJson(new InputStreamReader(inputStream), RadixUniverseConfig.class);
	}

	RadixUniverseConfig(
		List<Atom> genesis,
		long port,
		String name,
		String description,
		RadixUniverseType type,
		long timestamp,
		ECPublicKey creator,
		long magic
	) {
		this.genesis = Collections.unmodifiableList(genesis);
		this.name = name;
		this.description = description;
		this.type = type;
		this.timestamp = timestamp;
		this.creator = creator;
		this.port = port;
		this.magic = magic;
	}

	// TODO: should this be long?
	public int getMagic() {
		return (int) magic;
	}

	public byte getMagicByte() {
		return (byte) (magic & 0xff);
	}

	public ECPublicKey getSystemPublicKey() {
		return creator;
	}

	public RadixAddress getSystemAddress() {
		return new RadixAddress(this, creator);
	}

	public List<Atom> getGenesis() {
		return genesis;
	}

	public RadixHash getHash() {
		return RadixHash.of(Dson.getInstance().toDson(this));
	}

	@Override
	public String toString() {
		return name + " " + magic;
	}

	@Override
	public int hashCode() {
		return getHash().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof RadixUniverseConfig)) {
			return false;
		}

		return this.getHash().equals(((RadixUniverseConfig) o).getHash());
	}
}
