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
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializeWithHid;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

//TODO: cleanup and refactor this
@SerializerId2("radix.universe")
@SerializeWithHid
public class Universe {

	/**
	 * Universe builder.
	 */
	public static class Builder {
		private String name;
		private String description;
		private UniverseType type;
		private List<Txn> txns;
		private LedgerProof proof;

		private Builder() {
			// Nothing to do here
		}

		/**
		 * Sets the name of the universe.
		 * Ideally the universe name is a short identifier for the universe.
		 *
		 * @param name The name of the universe.
		 *
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder name(String name) {
			this.name = requireNonNull(name);
			return this;
		}

		/**
		 * Set the description of the universe.
		 * The universe description is a longer description of the universe.
		 *
		 * @param description The description of the universe.
		 *
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder description(String description) {
			this.description = requireNonNull(description);
			return this;
		}

		/**
		 * Sets the type of the universe, one of {@link UniverseType}.
		 *
		 * @param type The type of the universe.
		 *
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder type(UniverseType type) {
			this.type = requireNonNull(type);
			return this;
		}

		/**
		 * Adds an atom to the genesis atom list.
		 *
		 * @param genesisTxns The atoms to add to the genesis atom list.
		 *
		 * @return A reference to {@code this} to allow method chaining.
		 */
		public Builder setTxnsAndProof(VerifiedTxnsAndProof genesisTxns) {
			requireNonNull(genesisTxns);
			this.txns = genesisTxns.getTxns();
			this.proof = genesisTxns.getProof();

			return this;
		}

		/**
		 * Validate and build a universe from the specified data.
		 *
		 * @return The freshly build universe object.
		 */
		public Universe build() {
			require(this.name, "Name");
			require(this.description, "Description");
			require(this.type, "Universe type");
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
	 * @param type universe type to use when calculating universe magic
	 *
	 * @return The universe magic
	 */
	public static int computeMagic(UniverseType type) {
		return type.ordinal();
	}

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public enum UniverseType {
		PRODUCTION,
		TEST,
		DEVELOPMENT
	}

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

	private UniverseType type;

	@JsonProperty("genesis")
	@DsonOutput(Output.ALL)
	private List<byte[]> genesis;

	@JsonProperty("proof")
	@DsonOutput(Output.ALL)
	private LedgerProof proof;

	Universe() {
		// No-arg constructor for serializer
	}

	private Universe(Builder builder) {
		this.name = builder.name;
		this.description = builder.description;
		this.type = builder.type;
		this.proof = builder.proof;
		this.genesis = builder.txns == null
			? List.of()
			: builder.txns.stream().map(Txn::getPayload).collect(Collectors.toList());
	}

	/**
	 * Magic identifier for Universe.
	 */
	@JsonProperty("magic")
	@DsonOutput(value = Output.HASH, include = false)
	public int getMagic() {
		return computeMagic(type);
	}

	/**
	 * The name of Universe.
	 */
	public String getName() {
		return name;
	}

	/**
	 * The Universe description.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Whether this is a production Universe.
	 */
	public boolean isProduction() {
		return type.equals(UniverseType.PRODUCTION);
	}

	/**
	 * Whether this is a test Universe.
	 */
	public boolean isTest() {
		return type.equals(UniverseType.TEST);
	}

	/**
	 * Whether this is a development Universe.
	 */
	public boolean isDevelopment() {
		return type.equals(UniverseType.DEVELOPMENT);
	}

	/**
	 * Gets this Universe's immutable Genesis collection.
	 */
	public VerifiedTxnsAndProof getGenesis() {
		var txns = genesis.stream().map(Txn::create).collect(Collectors.toList());
		return VerifiedTxnsAndProof.create(txns, proof);
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
}
