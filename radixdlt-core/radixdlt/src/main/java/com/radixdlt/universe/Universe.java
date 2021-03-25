/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.universe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atom.Atom;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.SerializeWithHid;
import com.radixdlt.utils.Bytes;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

@SerializerId2("radix.universe")
@SerializeWithHid
public class Universe {

	/**
	 * Universe builder.
	 */
	public static class Builder {
		private Integer port;
		private String name;
		private String description;
		private UniverseType type;
		private Long timestamp;
		private ECPublicKey creator;
		private List<Atom> atoms;

		private Builder() {
			// Nothing to do here
		}

		/**
		 * Sets the TCP/UDP port for the universe.
		 *
		 * @param port The TCP/UDP port for the universe to use, {@code 0 <= port <= 65,535}.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder port(int port) {
			if (port < 0 || port > 65535) {
				throw new IllegalArgumentException("Invalid port number: " + port);
			}
			this.port = port;
			return this;
		}

		/**
		 * Sets the name of the universe.
		 * Ideally the universe name is a short identifier for the universe.
		 *
		 * @param name The name of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		/**
		 * Set the description of the universe.
		 * The universe description is a longer description of the universe.
		 *
		 * @param description The description of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder description(String description) {
			this.description = Objects.requireNonNull(description);
			return this;
		}

		/**
		 * Sets the type of the universe, one of {@link UniverseType}.
		 *
		 * @param type The type of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder type(UniverseType type) {
			this.type = Objects.requireNonNull(type);
			return this;
		}

		/**
		 * Sets the creation timestamp of the universe.
		 *
		 * @param timestamp The creation timestamp of the universe.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder timestamp(long timestamp) {
			if (timestamp < 0) {
				throw new IllegalArgumentException("Invalid timestamp: " + timestamp);
			}
			this.timestamp = timestamp;
			return this;
		}

		/**
		 * Sets the universe creators public key.
		 *
		 * @param creator The universe creators public key.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder creator(ECPublicKey creator) {
			this.creator = Objects.requireNonNull(creator);
			return this;
		}

		/**
		 * Adds an atom to the genesis atom list.
		 *
		 * @param genesisAtoms The atoms to add to the genesis atom list.
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder setAtoms(List<Atom> genesisAtoms) {
			Objects.requireNonNull(genesisAtoms);
			this.atoms = genesisAtoms;
			return this;
		}

		/**
		 * Validate and build a universe from the specified data.
		 *
		 * @return The freshly build universe object.
		 */
		public Universe build() {
			require(this.port, "Port number");
			require(this.name, "Name");
			require(this.description, "Description");
			require(this.type, "Universe type");
			require(this.timestamp, "Timestamp");
			require(this.creator, "Creator");
			return new Universe(this);
		}

		private void require(Object item, String what) {
			if (item == null) {
				throw new IllegalStateException(what + " must be specified");
			}
		}
	}

	/**
	 * Construct a new {@link Builder}.
	 *
	 * @return The freshly constructed builder.
	 */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Computes universe magic number from specified parameters.
	 *
	 * @param creator {@link ECPublicKey} of universe creator to use when calculating universe magic
	 * @param timestamp universe timestamp to use when calculating universe magic
	 * @param port universe port to use when calculating universe magic
	 * @param type universe type to use when calculating universe magic
	 * @return The universe magic
	 */
	public static int computeMagic(ECPublicKey creator, long timestamp, int port, UniverseType type) {
		return 31 * ((int) creator.euid().getLow()) * 13 * (int) timestamp * 7 * port + type.ordinal();
	}

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public enum UniverseType {
		PRODUCTION,
		TEST,
		DEVELOPMENT;
	}

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String 		name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String 		description;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private long 		timestamp;

	@JsonProperty("port")
	@DsonOutput(Output.ALL)
	private int			port;

	private UniverseType type;

	@JsonProperty("genesis")
	@DsonOutput(Output.ALL)
	private List<Atom> genesis;

	private ECPublicKey creator;

	private ECDSASignature signature;
	private BigInteger sigR;
	private BigInteger sigS;

	Universe() {
		// No-arg constructor for serializer
	}

	private Universe(Builder builder) {
		super();

		this.port = builder.port.intValue();
		this.name = builder.name;
		this.description = builder.description;
		this.type = builder.type;
		this.timestamp = builder.timestamp.longValue();
		this.creator = builder.creator;
		this.genesis = builder.atoms;
	}

	/**
	 * Magic identifier for Universe.
	 *
	 * @return
	 */
	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	public int getMagic() {
		return computeMagic(creator, timestamp, port, type);
	}

	/**
	 * The name of Universe.
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * The Universe description.
	 *
	 * @return
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * The default TCP/UDP port for the Universe.
	 *
	 * @return
	 */
	public int getPort() {
		return port;
	}

	/**
	 * The UTC 'BigBang' timestamp for the Universe.
	 *
	 * @return
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Whether this is a production Universe.
	 *
	 * @return
	 */
	public boolean isProduction() {
		return type.equals(UniverseType.PRODUCTION);
	}

	/**
	 * Whether this is a test Universe.
	 *
	 * @return
	 */
	public boolean isTest() {
		return type.equals(UniverseType.TEST);
	}

	/**
	 * Whether this is a development Universe.
	 *
	 * @return
	 */
	public boolean isDevelopment() {
		return type.equals(UniverseType.DEVELOPMENT);
	}

	/**
	 * Gets this Universe's immutable Genesis collection.
	 *
	 * @return
	 */
	public List<Atom> getGenesis() {
		return genesis;
	}

	/**
	 * Get creator key.
	 *
	 * @return
	 */
	public ECPublicKey getCreator() {
		return creator;
	}

	public ECDSASignature getSignature() {
		return signature;
	}

	public void setSignature(ECDSASignature signature) {
		this.signature = signature;
	}

	public static void sign(Universe universe, ECKeyPair key, Hasher hasher) {
		universe.setSignature(key.sign(hasher.hash(universe)));
	}

	public static boolean verify(Universe universe, ECPublicKey key, Hasher hasher) {
		return key.verify(hasher.hash(universe), universe.getSignature());
	}

	// Type - 1 getter, 1 setter
	@JsonProperty("type")
	@DsonOutput(Output.ALL)
	private int getJsonType() {
		return this.type.ordinal();
	}

	@JsonProperty("type")
	private void setJsonType(int type) {
		this.type = UniverseType.values()[type];
	}

	// Signature - 1 getter, 1 setter.
	@JsonProperty("creator")
	@DsonOutput(Output.ALL)
	private byte[] getJsonCreator() {
		return this.creator.getBytes();
	}

	@JsonProperty("creator")
	private void setJsonCreator(byte[] bytes) throws PublicKeyException {
		this.creator = ECPublicKey.fromBytes(bytes);
	}

	// Signature - 2 getters, 2 setters.
	@JsonProperty("signature.r")
	@DsonOutput(value = Output.HASH, include = false)
	private byte[] getJsonSignatureR() {
		return Bytes.trimLeadingZeros(signature.getR().toByteArray());
	}

	@JsonProperty("signature.s")
	@DsonOutput(value = Output.HASH, include = false)
	private byte[] getJsonSignatureS() {
		return Bytes.trimLeadingZeros(signature.getS().toByteArray());
	}

	@JsonProperty("signature.r")
	private void setJsonSignatureR(byte[] r) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.sigR = new BigInteger(1, r);
		if (this.sigS != null) {
			signature = new ECDSASignature(this.sigR, this.sigS);
			this.sigS = null;
			this.sigR = null;
		}
	}

	@JsonProperty("signature.s")
	private void setJsonSignatureS(byte[] s) {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		this.sigS = new BigInteger(1, s);
		if (this.sigR != null) {
			signature = new ECDSASignature(this.sigR, this.sigS);
			this.sigS = null;
			this.sigR = null;
		}
	}
}
