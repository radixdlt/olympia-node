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

import com.google.common.base.Suppliers;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Base58;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * base58 address based on a public key
 */
public final class RadixAddress {
	/**
	 * The public key this address represents
	 */
	private final ECPublicKey publicKey;

	/**
	 * The unique string which maps this address represents
	 */
	private final byte[] addressBytes;

	/**
	 * The Base58 formatted string of this address
	 */
	private final Supplier<String> base58 = Suppliers.memoize(this::computeBase58);

	/**
	 * The magic byte of this address
	 */
	private final transient int magicByte;

	public RadixAddress(byte magic, ECPublicKey publicKey) {
		this.publicKey = Objects.requireNonNull(publicKey);

		byte[] digest = publicKey.getCompressedBytes();
		byte[] addressBytes = new byte[1 + digest.length + 4];
		addressBytes[0] = magic;
		System.arraycopy(digest, 0, addressBytes, 1, digest.length);
		byte[] check = HashUtils.sha256(addressBytes, 0, digest.length + 1).asBytes();
		System.arraycopy(check, 0, addressBytes, digest.length + 1, 4);

		this.addressBytes = addressBytes;
		this.magicByte = magic;
	}

	public static RadixAddress from(byte[] raw) {
		try {
			byte[] check = HashUtils.sha256(raw, 0, raw.length - 4).asBytes();
			for (int i = 0; i < 4; ++i) {
				if (check[i] != raw[raw.length - 4 + i]) {
					throw new IllegalArgumentException("Address " + Base58.toBase58(raw) + " checksum mismatch");
				}
			}

			byte[] digest = new byte[raw.length - 5];
			System.arraycopy(raw, 1, digest, 0, raw.length - 5);

			return new RadixAddress(raw[0], ECPublicKey.fromBytes(digest));
		} catch (PublicKeyException e) {
			throw new IllegalArgumentException("Unable to create address from string: " + Base58.toBase58(raw), e);
		}
	}

	public int getMagic() {
		return addressBytes[0];
	}

	public static RadixAddress from(String address) {
		byte[] raw = Base58.fromBase58(address);
		return from(raw);
	}

	public static Optional<RadixAddress> fromString(String address) {
		try {
			byte[] raw = Base58.fromBase58(address);
			return Optional.of(from(raw));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(addressBytes, addressBytes.length);
	}

	public ECPublicKey getPublicKey() {
		return this.publicKey;
	}

	public EUID euid() {
		return this.publicKey.euid();
	}

	@Override
	public String toString() {
		return this.base58.get();
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(addressBytes);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof RadixAddress) {
			RadixAddress other = (RadixAddress) o;
			return Arrays.equals(this.addressBytes, other.addressBytes);
		}
		return false;
	}

	private String computeBase58() {
		return Base58.toBase58(addressBytes);
	}

	// ###  Methods from Client Library ###

	public int getMagicByte() {
		return magicByte;
	}

	public boolean ownsKey(ECKeyPair ecKeyPair) {
		return this.ownsKey(ecKeyPair.getPublicKey());
	}

	public boolean ownsKey(ECPublicKey publicKey) {
		return this.publicKey.equals(publicKey);
	}
}
