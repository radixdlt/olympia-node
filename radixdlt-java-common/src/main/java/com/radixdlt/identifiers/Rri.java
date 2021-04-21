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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class Rri {
	private static final String NAME_REGEX = "[a-z0-9]+";
	private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	public static final Rri NATIVE_TOKEN;
	public static final int HASH_BYTES = 26;

	static {
		 NATIVE_TOKEN = ofSystem("xrd");
	}

	private final byte[] hash;
	private final String name;

	Rri(byte[] hash, String name) {
		this.hash = hash;
		this.name = name;
	}

	private static Rri create(byte[] hash, String name) {
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("RRI name invalid, must match regex '" + NAME_REGEX + "': " + name);
		}
		Objects.requireNonNull(hash);

		return new Rri(hash, name);
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

	public String getName() {
		return name;
	}

	public static Rri of(byte[] hash, String name) {
		return create(hash, name);
	}

	public static Rri of(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		return create(pkToHash(name, key), name);
	}

	public static Rri ofSystem(String name) {
		return create(new byte[0], name);
	}

	public static Rri fromBech32(String s) {
		var d = Bech32.decode(s);
		var hash = d.data;
		if (hash.length > 0) {
			hash = Bits.convertBits(hash, 0, hash.length, 5, 8, false);
		}
		if (!d.hrp.endsWith("_rr")) {
			throw new IllegalArgumentException("Rri must end in _rr");
		}
		return create(hash, d.hrp.substring(0, d.hrp.length() - 3));
	}

	@Override
	public String toString() {
		final byte[] convert;
		if (hash.length != 0) {
			convert = Bits.convertBits(hash, 0, hash.length, 8, 5, true);
		} else {
			convert = hash;
		}
		return Bech32.encode(name.toLowerCase() + "_rr", convert);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Rri)) {
			return false;
		}

		var rri = (Rri) o;
		return Arrays.equals(rri.hash, hash)
			&& Objects.equals(rri.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), name);
	}
}