package com.radixdlt.client.core.address;

import com.google.gson.JsonElement;
import com.radixdlt.client.core.serialization.Dson;
import com.radixdlt.client.core.serialization.RadixJson;
import java.util.Collections;
import java.util.List;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import org.bouncycastle.util.encoders.Base64;

public class RadixUniverseConfig {

	private final int magic;
	private final int port;
	private final String name;
	private final String description;
	private final RadixUniverseType type;
	private final long timestamp;
	private final ECPublicKey creator;
	private final List<Atom> genesis;

	public static RadixUniverseConfig fromDson(String universeDson) {
		JsonElement jsonElement = Dson.getInstance().parse(Base64.decode(universeDson));
		RadixUniverseConfig universe = RadixJson.getGson().fromJson(jsonElement, RadixUniverseConfig.class);
		return universe;
	}

	RadixUniverseConfig(List<Atom> genesis, int port, String name, String description, RadixUniverseType type, long timestamp, ECPublicKey creator, int magic) {
		this.genesis = Collections.unmodifiableList(genesis);
		this.name = name;
		this.description = description;
		this.type = type;
		this.timestamp = timestamp;
		this.creator = creator;
		this.port = port;
		this.magic = magic;
	}

	public int getMagic() {
		return magic;
	}

	public byte getMagicByte() {
		return (byte)(magic & 0xff);
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

	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		RadixUniverseConfig c = (RadixUniverseConfig)o;
		if (magic != c.magic) return false;
		if (port != c.port) return false;
		if (!name.equals(c.name)) return false;
		if (!type.equals(c.type)) return false;
		if (timestamp != c.timestamp) return false;
		if (!creator.equals(c.creator)) return false;

		return true;
	}
}
