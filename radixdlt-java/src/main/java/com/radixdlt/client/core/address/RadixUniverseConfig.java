package com.radixdlt.client.core.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.ByteStreams;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.RadixConstants;
import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

@SerializerId2("radix.universe")
public class RadixUniverseConfig {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(Output.ALL)
	private short version = 100;

	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	private long magic;

	@JsonProperty("port")
	@DsonOutput(Output.ALL)
	private long port;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

	private RadixUniverseType type;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long timestamp;

	private ECPublicKey creator;

	@JsonProperty("genesis")
	@DsonOutput(Output.ALL)
	private List<Atom> genesis;

	public static RadixUniverseConfig fromDsonBase64(String dsonBase64) {
		byte[] bytes = Base64.decode(dsonBase64);
		RadixUniverseConfig universe = null;
		try {
			universe = Serialize.getInstance().fromDson(bytes, RadixUniverseConfig.class);
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to deserialize bytes", e);
		}
		return universe;
	}

	public static RadixUniverseConfig fromInputStream(InputStream inputStream) {
		try {
			byte[] bytes = ByteStreams.toByteArray(inputStream);
			String json = new String(bytes, RadixConstants.STANDARD_CHARSET);
			return Serialize.getInstance().fromJson(json, RadixUniverseConfig.class);
		} catch (IOException e) {
			throw new UncheckedIOException("Reading universe configuration", e);
		}
	}

	RadixUniverseConfig() {
		// No-arg constructor for serializer only
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
		return new RadixAddress((byte) (this.magic & 0xff), creator);
	}

	public List<Atom> getGenesis() {
		return genesis;
	}

	public Hash getHash() {
		try {
			return new Hash(Serialize.getInstance().toDson(this, Output.HASH));
		} catch (SerializationException e) {
			throw new IllegalStateException("Failed to serialize universe config", e);
		}
	}

	public EUID getHid() {
		return this.getHash().euid();
	}

	@Override
	public String toString() {
		return name + " " + magic + " " + getHid();
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

	// Signature - 1 getter, 1 setter.
	// Better option would be to make public keys primitive types as the are
	// very common, or alternatively serialize as an embedded object.
	@JsonProperty("creator")
	@DsonOutput(Output.ALL)
	private byte[] getJsonCreator() {
		return this.creator.getBytes();
	}

	@JsonProperty("creator")
	private void setJsonCreator(byte[] bytes) {
		try {
			this.creator = new ECPublicKey(bytes);
		} catch (CryptoException e) {
			throw new IllegalArgumentException("Failed to create public key from bytes", e);
		}
	}

	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private int getJsonType() {
		return this.type.ordinalValue();
	}

	@JsonProperty("type")
	private void setJsonType(int type) {
		this.type = RadixUniverseType.valueOf(type);
	}
}
