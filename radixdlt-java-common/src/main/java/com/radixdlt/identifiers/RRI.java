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
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.utils.functional.Result;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class RRI {
	private static final String NAME_REGEX = "[a-z0-9]+";
	private static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

	private final byte[] hash;
	private final String name;

	RRI(byte[] hash, String name) {
		if (!NAME_PATTERN.matcher(name).matches()) {
			throw new IllegalArgumentException("RRI name invalid, must match regex '" + NAME_REGEX + "': " + name);
		}

		this.hash = hash;
		this.name = name;
	}

	private static byte[] pkToHash(String name, ECPublicKey publicKey) {
		var nameBytes = name.getBytes(StandardCharsets.UTF_8);
		var dataToHash = new byte[33 + nameBytes.length];
		System.arraycopy(publicKey.getCompressedBytes(), 0, dataToHash, 0, 33);
		System.arraycopy(nameBytes, 0, dataToHash, 33, nameBytes.length);
		var firstHash = HashUtils.sha256(dataToHash);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return Arrays.copyOfRange(secondHash.asBytes(), 12, 32);
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

	public String getName() {
		return name;
	}

	public static RRI of(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		return new RRI(pkToHash(name, key), name);
	}

	public static RRI ofSystem(String name) {
		return new RRI(new byte[0], name);
	}

	public static RRI fromBech32(String s) {
		var d = Bech32.decode(s);
		var hash = d.data;
		if (hash.length > 0) {
			hash = convertBits(hash, 0, hash.length, 5, 8, false);
		}
		if (!d.hrp.endsWith("_rr")) {
			throw new IllegalArgumentException("Rri must end in _rr");
		}
		return new RRI(hash, d.hrp.substring(0, d.hrp.length() - 3));
	}

	private static byte[] convertBits(final byte[] in, final int inStart, final int inLen, final int fromBits,
									  final int toBits, final boolean pad) throws AddressFormatException {
		int acc = 0;
		int bits = 0;
		ByteArrayOutputStream out = new ByteArrayOutputStream(64);
		final int maxv = (1 << toBits) - 1;
		final int maxAcc = (1 << (fromBits + toBits - 1)) - 1;
		for (int i = 0; i < inLen; i++) {
			int value = in[i + inStart] & 0xff;
			if ((value >>> fromBits) != 0) {
				throw new AddressFormatException(
					String.format("Input value '%X' exceeds '%d' bit size", value, fromBits));
			}
			acc = ((acc << fromBits) | value) & maxAcc;
			bits += fromBits;
			while (bits >= toBits) {
				bits -= toBits;
				out.write((acc >>> bits) & maxv);
			}
		}
		if (pad) {
			if (bits > 0) {
				out.write((acc << (toBits - bits)) & maxv);
			}
		} else if (bits >= fromBits || ((acc << (toBits - bits)) & maxv) != 0) {
			throw new AddressFormatException("Could not convert bits, invalid padding");
		}
		return out.toByteArray();
	}

	public static Result<RRI> fromString(String s) {
		try {
			return Result.ok(fromBech32(s));
		} catch (RuntimeException e) {
			return Result.fail("Error while parsing RRI: {0}", e.getMessage());
		}
	}

	@Override
	public String toString() {
		final byte[] convert;
		if (hash.length != 0) {
			convert = convertBits(hash, 0, hash.length, 8, 5, true);
		} else {
			convert = hash;
		}
		return Bech32.encode(name + "_rr", convert);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RRI)) {
			return false;
		}

		RRI rri = (RRI) o;
		return Arrays.equals(rri.hash, hash) && Objects.equals(rri.name, name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(hash), name);
	}
}