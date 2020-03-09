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

package com.radixdlt.consensus.safety;

import com.google.inject.Inject;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.crypto.Hash;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;

import java.util.Objects;

public class SimpleVertexHasher implements VertexHasher {
	private final Serialization serialization;

	@Inject
	public SimpleVertexHasher(Serialization serialization) {
		this.serialization = Objects.requireNonNull(serialization);
	}

	@Override
	public Hash hash(VertexMetadata vertexMetadata) {
		try {
			return new Hash(this.serialization.toDson(vertexMetadata, DsonOutput.Output.HASH));
		} catch (SerializationException e) {
			throw new RuntimeException("Failed to hash " + vertexMetadata, e);
		}
	}
}
