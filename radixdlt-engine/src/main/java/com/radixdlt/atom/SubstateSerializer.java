/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.atom;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationUtils;
import com.radixdlt.utils.functional.Result;

public final class SubstateSerializer {
	private static final Serialization serialization = DefaultSerialization.getInstance();

	private SubstateSerializer() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static Particle deserialize(byte[] bytes) throws DeserializeException {
		return serialization.fromDson(bytes, Particle.class);
	}

	public static Particle deserialize(byte[] bytes, int offset) throws DeserializeException {
		return serialization.fromDson(bytes, offset, bytes.length - offset, Particle.class);
	}

	public static byte[] serialize(Particle p) {
		return serialization.toDson(p, DsonOutput.Output.ALL);
	}

	public static Result<Particle> deserializeFromBytes(byte[] bytes) {
		return SerializationUtils.restore(serialization, bytes, Particle.class);
	}
}
