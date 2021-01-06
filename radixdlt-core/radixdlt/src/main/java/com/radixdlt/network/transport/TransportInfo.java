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

package com.radixdlt.network.transport;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.DsonOutput.Output;

@SerializerId2("network.transport_info")
public final class TransportInfo {

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private final String name;

	@JsonProperty("metadata")
	@DsonOutput(Output.ALL)
	private final TransportMetadata metadata;

	public static TransportInfo of(String name, TransportMetadata metadata) {
		return new TransportInfo(name, metadata);
	}

	TransportInfo() {
		this.metadata = null;
		this.name = null;
	}

	private TransportInfo(String name, TransportMetadata metadata) {
		this.name = Objects.requireNonNull(name);
		this.metadata = Objects.requireNonNull(metadata);
	}

	public String name() {
		return this.name;
	}

	public TransportMetadata metadata() {
		return this.metadata;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.metadata);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof TransportInfo) {
			TransportInfo other = (TransportInfo) obj;
			return Objects.equals(this.name, other.name) && Objects.equals(this.metadata, other.metadata);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]", getClass().getSimpleName(), name, metadata);
	}
}
