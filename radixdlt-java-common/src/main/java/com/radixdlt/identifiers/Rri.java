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

package com.radixdlt.identifiers;

import com.radixdlt.crypto.HashUtils;
import org.bitcoinj.core.Bech32;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Bits;
import com.radixdlt.utils.functional.Result;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class Rri {
	public static final int HASH_BYTES = 26;
	private final byte[] hash;

	Rri(byte[] hash) {
		this.hash = hash;
	}

	private static Rri create(byte[] hash) {
		return new Rri(hash);
	}

	public static byte[] pkToHash(String name, ECPublicKey publicKey) {
		var nameBytes = name.getBytes(StandardCharsets.UTF_8);
		var dataToHash = new byte[33 + nameBytes.length];
		System.arraycopy(publicKey.getCompressedBytes(), 0, dataToHash, 0, 33);
		System.arraycopy(nameBytes, 0, dataToHash, 33, nameBytes.length);
		var firstHash = HashUtils.sha256(dataToHash);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return Arrays.copyOfRange(secondHash.asBytes(), 32 - HASH_BYTES, 32);
	}

	public boolean isSystem() {
		return hash.length == 0;
	}

	public byte[] getHash() {
		return hash;
	}

	public static Rri of(byte[] hash) {
		return new Rri(hash);
	}

	public static Rri of(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		return create(pkToHash(name, key));
	}

	public static Rri ofNativeToken() {
		return create(new byte[0]);
	}

	@Override
	public String toString() {
		return Hex.toHexString(this.hash);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Rri)) {
			return false;
		}

		var rri = (Rri) o;
		return Arrays.equals(rri.hash, hash);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(hash);
	}
}