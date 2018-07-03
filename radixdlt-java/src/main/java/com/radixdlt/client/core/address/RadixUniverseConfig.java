package com.radixdlt.client.core.address;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.google.gson.JsonObject;
import com.radixdlt.client.core.serialization.RadixJson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public class RadixUniverseConfig {

	private final int magic;
	private final int port;
	private final String name;
	private final String description;
	private final RadixUniverseType type;
	private final long timestamp;
	private final ECPublicKey creator;
	private final List<Atom> genesis;

	public static RadixUniverseConfig fromInputStream(InputStream inputStream) {
		return RadixJson.getGson().fromJson(new InputStreamReader(inputStream), RadixUniverseConfig.class);
	}

	RadixUniverseConfig(List<Atom> genesis, int port, String name, String description, RadixUniverseType type,
                        long timestamp, ECPublicKey creator, int magic) {
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

	public JsonObject toJson() {
		JsonObject universe = new JsonObject();
		universe.addProperty("magic", magic);
		universe.addProperty("port", port);
		universe.addProperty("name", name);
		universe.addProperty("description", description);
		universe.add("type", RadixJson.getGson().toJsonTree(type));
		universe.addProperty("timestamp", timestamp);
		universe.add("creator", RadixJson.getGson().toJsonTree(creator));
		universe.add("genesis", RadixJson.getGson().toJsonTree(genesis));

		return universe;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int hashCode() {
		// TODO: fix this
		return (magic + ":" + port + ":" + name + ":" + timestamp).hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof RadixUniverseConfig)) {
			return false;
		}

		RadixUniverseConfig c = (RadixUniverseConfig) o;
		if (magic != c.magic) {
			return false;
		}
		if (port != c.port) {
			return false;
		}
		if (!name.equals(c.name)) {
			return false;
		}
		if (!type.equals(c.type)) {
			return false;
		}
		if (timestamp != c.timestamp) {
			return false;
		}
		if (!creator.equals(c.creator)) {
			return false;
		}

		return true;
	}
}
