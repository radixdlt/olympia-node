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

package com.radixdlt.utils;

import com.radixdlt.crypto.Hash;
import java.nio.ByteBuffer;
import java.util.Objects;

public class POW {
	private final int magic;
	private final Hash seed;
	private final long nonce;
	private final ByteBuffer buffer = ByteBuffer.allocate(32 + 4 + Long.BYTES);

	public POW(int magic, Hash seed) {
		this(magic, seed, Long.MIN_VALUE);
	}

	public POW(int magic, Hash seed, long nonce) {
		Objects.requireNonNull(seed);

		this.magic = magic;
		this.seed = seed;
		this.nonce = nonce;
	}

	public int getMagic() {
		return magic;
	}

	public Hash getSeed() {
		return seed;
	}

	public long getNonce() {
		return nonce;
	}

	public synchronized Hash getHash() {
		buffer.clear();
		buffer.putInt(magic);
		buffer.put(seed.toByteArray());
		buffer.putLong(nonce);
		buffer.flip();

		return Hash.of(buffer.array());
	}
}
