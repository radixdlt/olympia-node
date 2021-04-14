/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.address;

import org.bouncycastle.util.encoders.Base64;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.hash.HashCode;
import com.google.common.io.ByteStreams;
import com.radixdlt.atom.Txn;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.RadixConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

@SerializerId2("radix.universe")
public class RadixUniverseConfig {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

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
	private List<byte[]> genesis;

	public static RadixUniverseConfig fromDsonBase64(String dsonBase64) {
		byte[] bytes = Base64.decode(dsonBase64);
		RadixUniverseConfig universe = null;
		try {
			universe = Serialize.getInstance().fromDson(bytes, RadixUniverseConfig.class);
		} catch (DeserializeException e) {
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
		List<byte[]> genesis,
		long port,
		String name,
		String description,
		RadixUniverseType type,
		long timestamp,
		ECPublicKey creator,
		long magic
	) {
		this.genesis = genesis;
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

	public long timestamp() {
		return this.timestamp;
	}

	public List<Txn> getGenesis() {
		return genesis.stream().map(Txn::create).collect(Collectors.toList());
	}

	public HashCode getHash() {
		return HashUtils.sha256(Serialize.getInstance().toDson(this, Output.HASH));
	}

	public EUID euid() {
		return EUID.fromHash(this.getHash());
	}

	@Override
	public String toString() {
		return name + " " + magic + " " + euid();
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
			this.creator = ECPublicKey.fromBytes(bytes);
		} catch (PublicKeyException e) {
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
