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

package com.radixdlt.serialization;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.functional.Result;

import java.nio.charset.StandardCharsets;

/**
 * Collection of Serialization-related utilities
 */
public class SerializationUtils {
	private static final HashFunction murmur3_128 = Hashing.murmur3_128();

	private SerializationUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static EUID stringToNumericID(String id) {
		var h = murmur3_128.hashBytes(id.getBytes(StandardCharsets.UTF_8));
		return new EUID(h.asBytes());
	}

	public static <T> Result<T> restore(Serialization serialization, byte[] data, Class<T> clazz) {
		try {
			return Result.ok(serialization.fromDson(data, clazz));
		} catch (DeserializeException e) {
			return Result.fail("Unable to deserialize {0}", clazz.getSimpleName());
		}
	}
}
