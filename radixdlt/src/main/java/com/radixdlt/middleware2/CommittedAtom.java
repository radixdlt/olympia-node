/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.middleware2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.SyncedStateComputer.CommittedInstruction;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.crypto.Hash;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * An atom which has been committed by the BFT
 *
 * TODO: add commit signature proof
 */
@Immutable
@SerializerId2("consensus.committed_atom")
public final class CommittedAtom implements LedgerAtom, CommittedInstruction {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
	SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("atom")
	@DsonOutput(Output.ALL)
	private final ClientAtom clientAtom;

	// TODO: include commit signature proof
	@JsonProperty("metadata")
	@DsonOutput(Output.ALL)
	private final VertexMetadata vertexMetadata;

	@JsonProperty("timestamp")
	@DsonOutput(Output.ALL)
	private final long timestamp;

	CommittedAtom() {
		// Serializer only
		this.clientAtom = null;
		this.vertexMetadata = null;
		this.timestamp = 0L;
	}

	public CommittedAtom(ClientAtom clientAtom, VertexMetadata vertexMetadata, long timestamp) {
		this.clientAtom = clientAtom;
		this.vertexMetadata = Objects.requireNonNull(vertexMetadata);
		this.timestamp = timestamp;
	}

	public ClientAtom getClientAtom() {
		return clientAtom;
	}

	@Override
	public VertexMetadata getVertexMetadata() {
		return vertexMetadata;
	}

	@Override
	public CMInstruction getCMInstruction() {
		return clientAtom.getCMInstruction();
	}

	@Override
	public AID getAID() {
		return clientAtom.getAID();
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.timestamp, this.clientAtom, this.vertexMetadata);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CommittedAtom)) {
			return false;
		}

		CommittedAtom other = (CommittedAtom) o;
		return this.timestamp == other.timestamp
			&& Objects.equals(other.clientAtom, this.clientAtom)
			&& Objects.equals(other.vertexMetadata, this.vertexMetadata);
	}

	@Override
	public ImmutableMap<String, String> getMetaData() {
		return clientAtom.getMetaData();
	}

	@Override
	public Hash getPowFeeHash() {
		return clientAtom.getPowFeeHash();
	}

	@Override
	public String toString() {
		return String.format("%s{atom=%s, timestamp=%s, meta=%s}",
			getClass().getSimpleName(), clientAtom != null ? clientAtom.getAID() : null, this.timestamp, this.vertexMetadata);
	}
}
