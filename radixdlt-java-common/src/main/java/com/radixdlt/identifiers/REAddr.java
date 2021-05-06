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

import org.bouncycastle.util.encoders.Hex;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A Radix Engine Address. A 1-34 byte array describing a resource or component
 * in the Radix Engine.
 *
 * The first byte of the address describes the type of address followed by
 * additional data depending on the type.
 *
 * Type (first byte)
 * 0x01 : Native Token, 0 data bytes
 * 0x03 : Hashed Key+Nonce, append lower_26_bytes(sha_256_twice(33_byte_compressed_pubkey | arg_nonce))
 * 0x04 : Public Key, append 33 bytes of a compressed EC public key
 */
public final class REAddr {
	public enum REAddrType {
		NATIVE_TOKEN((byte) 1) {
			public REAddr parse(ByteBuffer buf) {
				return REAddr.ofNativeToken();
			}

			public Optional<String> verify(ByteBuffer buf) {
				if (buf.hasRemaining()) {
					return Optional.of("Native token must not have bytes");
				}
				return Optional.empty();
			}
		},
		HASHED_KEY((byte) 3) {
			public REAddr parse(ByteBuffer buf) {
				var addr = new byte[REAddr.HASHED_KEY_BYTES + 1];
				addr[0] = type;
				buf.get(addr, 1, REAddr.HASHED_KEY_BYTES);
				return new REAddr(addr);
			}

			public Optional<String> verify(ByteBuffer buf) {
				if (buf.remaining() != REAddr.HASHED_KEY_BYTES) {
					return Optional.of("Hashed key must have " + HASHED_KEY_BYTES + " bytes");
				}
				return Optional.empty();
			}
		},
		PUB_KEY((byte) 4) {
			public REAddr parse(ByteBuffer buf) {
				var addr = new byte[ECPublicKey.COMPRESSED_BYTES + 1];
				addr[0] = type;
				buf.get(addr, 1, ECPublicKey.COMPRESSED_BYTES);
				return new REAddr(addr);
			}

			public Optional<String> verify(ByteBuffer buf) {
				if (buf.remaining() != ECPublicKey.COMPRESSED_BYTES) {
					return Optional.of("Pub key address must have " + ECPublicKey.COMPRESSED_BYTES + " bytes");
				}
				return Optional.empty();
			}
		};

		static Map<Byte, REAddrType> opMap;
		static {
			opMap = Arrays.stream(REAddrType.values())
				.collect(Collectors.toMap(REAddrType::byteValue, r -> r));
		}

		final byte type;

		REAddrType(byte type) {
			this.type = type;
		}

		public byte byteValue() {
			return type;
		}

		public abstract REAddr parse(ByteBuffer buf);

		public abstract Optional<String> verify(ByteBuffer buf);

		public static Optional<REAddrType> parse(byte b) {
			return Optional.ofNullable(opMap.get(b));
		}
	}

	private static final int HASHED_KEY_BYTES = 26;
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
			if (hash.length != 1 + HASHED_KEY_BYTES) {
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
		var hash = HashUtils.sha256(dataToHash);
		return Arrays.copyOfRange(hash.asBytes(), 32 - HASHED_KEY_BYTES, 32);
	}

	public boolean allowToClaimAddress(ECPublicKey publicKey, Optional<byte[]> arg) {
		if (addr[0] == REAddrType.HASHED_KEY.type) {
			return arg.map(a -> {
				var hash = REAddr.pkToHash(new String(a), publicKey);
				return Arrays.equals(addr, 1, HASHED_KEY_BYTES + 1, hash, 0, HASHED_KEY_BYTES);
			}).orElse(false);
		}

		return false;
	}

	public boolean isAccount() {
		return getType() == REAddrType.PUB_KEY;
	}

	public boolean allowToWithdrawFrom(ECPublicKey publicKey) {
		if (getType() != REAddrType.PUB_KEY) {
			return false;
		}

		return Arrays.equals(
			addr, 1, 1 + ECPublicKey.COMPRESSED_BYTES,
			publicKey.getCompressedBytes(), 0, ECPublicKey.COMPRESSED_BYTES
		);
	}

	public REAddrType getType() {
		return REAddrType.parse(addr[0]).orElseThrow();
	}

	public boolean isNativeToken() {
		return addr[0] == REAddrType.NATIVE_TOKEN.type;
	}

	public byte[] getBytes() {
		return addr;
	}

	public static REAddr of(byte[] addr) {
		if (addr.length == 0) {
			throw new IllegalArgumentException("Address cannot be empty.");
		}
		var buf = ByteBuffer.wrap(addr);
		var type = REAddrType.parse(buf.get());
		if (type.isEmpty()) {
			throw new IllegalArgumentException("Unknown address type: " + type);
		}
		var error = type.get().verify(buf);
		error.ifPresent(str -> {
			throw new IllegalArgumentException(str);
		});
		return new REAddr(addr);
	}

	public static REAddr ofHashedKey(ECPublicKey key, String name) {
		Objects.requireNonNull(key);
		var hash = pkToHash(name, key);
		var buf = ByteBuffer.allocate(HASHED_KEY_BYTES + 1);
		buf.put(REAddrType.HASHED_KEY.type);
		buf.put(hash);
		return create(buf.array());
	}

	public static REAddr ofPubKeyAccount(ECPublicKey key) {
		Objects.requireNonNull(key);
		var buf = ByteBuffer.allocate(ECPublicKey.COMPRESSED_BYTES + 1);
		buf.put(REAddrType.PUB_KEY.type);
		buf.put(key.getCompressedBytes());
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