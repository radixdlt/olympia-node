package com.radixdlt.atomos;

import com.google.common.base.Suppliers;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Base58;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

public final class RadixAddress
{
	/**
	 * The public key this address represents
	 */
	private final ECPublicKey key;

	/**
	 * The unique string which maps this address represents
	 */
	private final byte[] addressBytes;

	/**
	 * The Base58 formatted string of this address
	 */
	private final Supplier<String> base58 = Suppliers.memoize(this::computeBase58);

	public RadixAddress(byte magic, ECPublicKey key) {
		this.key = Objects.requireNonNull(key);

		byte[] digest = key.getBytes();
		byte[] addressBytes = new byte[1 + digest.length + 4];
		addressBytes[0] = magic;
		System.arraycopy (digest, 0, addressBytes, 1, digest.length);
		byte[] check = Hash.hash256 (addressBytes, 0, digest.length + 1);
		System.arraycopy (check, 0, addressBytes, digest.length + 1, 4);

		this.addressBytes = addressBytes;
	}

	public static RadixAddress from(Universe universe, ECPublicKey key) {
		return new RadixAddress((byte) (universe.getMagic() & 0xff), key);
	}

	public static RadixAddress from(byte[] raw) {
		try {
			byte[] check = Hash.hash256(raw, 0, raw.length - 4);
			for (int i = 0; i < 4; ++i) {
				if (check[i] != raw[raw.length - 4 + i]) {
					throw new IllegalArgumentException("Address " + Base58.toBase58(raw) + " checksum mismatch");
				}
			}

			byte[] digest = new byte[raw.length - 5];
			System.arraycopy(raw, 1, digest, 0, raw.length - 5);

			return new RadixAddress(raw[0], new ECPublicKey(digest));
		} catch (CryptoException e) {
			throw new IllegalArgumentException("Unable to create address from string: " + Base58.toBase58(raw), e);
		}
	}

	public static RadixAddress from(String address) {
		byte[] raw = Base58.fromBase58(address);
		return from(raw);
	}

	// TODO: remove clone
	public byte[] toByteArray() {
		return addressBytes.clone();
	}

	public ECPublicKey getKey() {
		return this.key;
	}

	public EUID getUID() {
		return this.key.getUID();
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
}
