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

import com.radixdlt.crypto.ECPublicKey;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A Radix resource identifier is a human readable unique identifier into the Ledger which points to a resource.
 */
public final class REAddr {
	public enum REAddrType {
		NATIVE_TOKEN((byte) 1) {
			public REAddr parse(ByteBuffer buf) {
				return REAddr.ofNativeToken();
			}
		},
		HASHED_KEY((byte) 3) {
			public REAddr parse(ByteBuffer buf) {
				var addr = new byte[REAddr.HASH_BYTES + 1];
				addr[0] = type;
				buf.get(addr, 1, REAddr.HASH_BYTES);
				return new REAddr(addr);
			}
		};

		final byte type;

		REAddrType(byte type) {
			this.type = type;
		}

		public abstract REAddr parse(ByteBuffer buf);

		public static Optional<REAddrType> parse(byte b) {
			switch (b) {
				case 1:
					return Optional.of(NATIVE_TOKEN);
				case 3:
					return Optional.of(HASHED_KEY);
				default:
					return Optional.empty();
			}
		}
	}

	public static final int HASH_BYTES = 26;
	private final byte[] addr;

	REAddr(byte[] addr) {
		this.addr = addr;
	}

	private static REAddr create(byte[] hash) {
		Objects.requireNonNull(hash);
		if (hash.length == 0) {
			throw new IllegalArgumentException();
		}
		if (hash[0] == REAddrType.NATIVE_TOKEN.type) {
			if (hash.length != 1) {
				throw new IllegalArgumentException();
			}
		} else if (hash[0] == REAddrType.HASHED_KEY.type) {
			if (hash.length != 1 + HASH_BYTES) {
				throw new IllegalArgumentException();
			}
		}

		return new REAddr(hash);
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

	public boolean allow(ECPublicKey publicKey, byte[] arg) {
		if (addr[0] == REAddrType.HASHED_KEY.type) {
			var hash = REAddr.pkToHash(new String(arg), publicKey);
			return Arrays.equals(addr, 1, HASH_BYTES + 1, hash, 0, HASH_BYTES);
		}

		return false;
	}

	public boolean isSystem() {
		return addr[0] == REAddrType.NATIVE_TOKEN.type;
	}

	public byte[] getBytes() {
		return addr;
	}

	public static REAddr of(byte[] addr) {
		return new REAddr(addr);
	}

	public static REAddr ofHashedKey(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		var hash = pkToHash(name, key);
		var buf = ByteBuffer.allocate(HASH_BYTES + 1);
		buf.put(REAddrType.HASHED_KEY.type);
		buf.put(hash);
		return create(buf.array());
	}

	public static REAddr ofNativeToken() {
		return create(new byte[] {REAddrType.NATIVE_TOKEN.type});
	}

	@Override
	public String toString() {
		return Hex.toHexString(this.addr);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof REAddr)) {
			return false;
		}

		var rri = (REAddr) o;
		return Arrays.equals(rri.addr, addr);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(addr);
	}
}