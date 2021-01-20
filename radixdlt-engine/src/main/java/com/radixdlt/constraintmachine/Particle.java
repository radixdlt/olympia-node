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

package com.radixdlt.constraintmachine;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializeWithHid;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.SerializerDummy;

import java.util.Set;

/**
 * A content-identifiable, sub-state of the ledger.
 *
 * TODO: Remove serialization stuff out of here
 */
@SerializerId2("radix.particle")
@SerializeWithHid
public abstract class Particle {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	public Particle() {
		// Nothing for now
	}

	public abstract Set<EUID> getDestinations();

	public static EUID euidOf(Particle particle, Hasher hasher) {
		return EUID.fromHash(hasher.hash(particle));
	}

}
