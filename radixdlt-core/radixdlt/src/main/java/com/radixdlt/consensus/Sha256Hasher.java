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

package com.radixdlt.consensus;

import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;

/**
 * A Hasher implementation that uses sha256 hashing algorithm.
 */
public class Sha256Hasher implements Hasher {

	private final Serialization serialization;

	public static Sha256Hasher withDefaultSerialization() {
		return new Sha256Hasher(DefaultSerialization.getInstance());
	}

	public Sha256Hasher(Serialization serialization) {
		this.serialization = serialization;
	}

	@Override
	public int bytes() {
		return 32;
	}

	@Override
	public HashCode hash(Object o) {
		return HashUtils.sha256(serialization.toDson(o, DsonOutput.Output.HASH));
	}

	@Override
	public HashCode hashBytes(byte[] bytes) {
		return HashUtils.sha256(bytes);
	}
}
