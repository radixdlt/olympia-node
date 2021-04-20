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

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.Bits;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.functional.Result;
import org.bitcoinj.core.Bech32;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class Rri {
	public static final int HASH_BYTES = 33;
	private static final String NAME_REGEX = "[a-z0-9]+";
	private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	private final byte[] hash;
	private final String name;

	Rri(byte[] hash, String name) {
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("RRI name invalid, must match regex '" + NAME_REGEX + "': " + name);
		}

		this.hash = hash;
		this.name = name;
	}

	/*
	private static byte[] pkToHash(String name, ECPublicKey publicKey) {
		var nameBytes = name.getBytes(StandardCharsets.UTF_8);
		var dataToHash = new byte[33 + nameBytes.length];
		System.arraycopy(publicKey.getCompressedBytes(), 0, dataToHash, 0, 33);
		System.arraycopy(nameBytes, 0, dataToHash, 33, nameBytes.length);
		var firstHash = HashUtils.sha256(dataToHash);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return Arrays.copyOfRange(secondHash.asBytes(), 12, 32);
	}
	 */


	private static byte[] pkToHash(String name, ECPublicKey publicKey) {
		return publicKey.getCompressedBytes();
	}

	public boolean ownedBy(ECPublicKey publicKey) {
		if (hash.length == 0) {
			return false;
		}

		return Arrays.equals(hash, pkToHash(name, publicKey));
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
		Objects.requireNonNull(hash);
		return new Rri(hash, name);
	}

	public static Rri of(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		return new Rri(pkToHash(name, key), name);
	}

	public static Rri ofSystem(String name) {
		return new Rri(new byte[0], name);
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
		return new Rri(hash, d.hrp.substring(0, d.hrp.length() - 3));
	}


	public static Result<Rri> fromString(String s) {
		try {
			//return Result.ok(fromBech32(s));
			return fromSpecString(s);
		} catch (RuntimeException e) {
			return Result.fail("Error while parsing RRI: {0}", e.getMessage());
		}
	}

	public String toSpecString(byte magic) {
		if (hash.length == 0) {
			return "//" + name.toUpperCase();
		} else {
			try {
				var address = new RadixAddress(magic, ECPublicKey.fromBytes(hash));
				return "/" + address + "/" + name.toUpperCase();
			} catch (PublicKeyException e) {
				throw new IllegalStateException();
			}
		}
	}

	public static Result<Rri> fromSpecString(String s) {
		String[] split = s.split("/", 3);
		if (split.length != 3 || split[0].length() != 0) {
			return Result.fail("RRI has invalid format");
		}

		var name = split[2].toLowerCase();

		if (!NAME_PATTERN.matcher(name).matches()) {
			return Result.fail("RRI name is invalid");
		}

		if (split[1].length() == 0) {
			return Result.ok(Rri.ofSystem(name));
		} else {
			return RadixAddress.fromString(split[1])
				.map(address -> new Rri(address.getPublicKey().getCompressedBytes(), name));
		}
	}

	@Override
	public String toString() {
		final byte[] convert;
		if (hash.length != 0) {
			convert = Bits.convertBits(hash, 0, hash.length, 8, 5, true);
		} else {
			convert = hash;
		}
		return Bech32.encode(name + "_rr", convert);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Rri)) {
			return false;
		}

		Rri rri = (Rri) o;
		return Arrays.equals(rri.hash, hash)
			&& Objects.equals(rri.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), name);
	}
}