package com.radixdlt.client.core.address;

import java.util.Objects;

import org.radix.common.ID.EUID;

import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.util.Base58;
import org.radix.serialization2.SerializerId2;

@SerializerId2("ADDRESS")
public class RadixAddress {

	// The Base58 address string
	private final String addressBase58;
	private final transient ECPublicKey publicKey;

	public RadixAddress(String addressBase58) {
		byte[] raw = Base58.fromBase58(addressBase58);
		RadixHash check = RadixHash.of(raw, 0, raw.length - 4);
		for (int i = 0; i < 4; ++i) {
			if (check.get(i) != raw[raw.length - 4 + i]) {
				throw new IllegalArgumentException("Address " + addressBase58 + " checksum mismatch");
			}
		}

		byte[] publicKey = new byte[raw.length - 5];
		System.arraycopy(raw, 1, publicKey, 0, raw.length - 5);

		this.addressBase58 = addressBase58;
		this.publicKey = new ECPublicKey(publicKey);
	}

	public RadixAddress(RadixUniverseConfig universe, ECPublicKey publicKey) {
		this(universe.getMagic(), publicKey);
	}

	public RadixAddress(int magic, ECPublicKey publicKey) {
		Objects.requireNonNull(publicKey);
		if (publicKey.length() != 33) {
			throw new IllegalArgumentException("Public key must be 33 bytes but was " + publicKey.length());
		}

		byte[] addressBytes = new byte[1 + publicKey.length() + 4];
		// Universe magic byte
		addressBytes[0] = (byte) (magic & 0xff);
		// Public Key
		publicKey.copyPublicKey(addressBytes, 1);
		// Checksum
		byte[] check = RadixHash.of(addressBytes, 0, publicKey.length() + 1).toByteArray();
		System.arraycopy(check, 0, addressBytes, publicKey.length() + 1, 4);

		this.addressBase58 = Base58.toBase58(addressBytes);
		this.publicKey = publicKey;
	}

	public boolean ownsKey(ECKeyPair ecKeyPair) {
		return this.ownsKey(ecKeyPair.getPublicKey());
	}

	public boolean ownsKey(ECPublicKey publicKey) {
		return this.publicKey.equals(publicKey);
	}

	@Override
	public String toString() {
		return addressBase58;
	}

	public EUID getUID() {
		return publicKey.getUID();
	}

	public ECPublicKey getPublicKey() {
		return publicKey;
	}

	public ECKeyPair toECKeyPair() {
		return new ECKeyPair(publicKey);
	}

	public static RadixAddress fromString(String addressBase58) {
		return new RadixAddress(addressBase58);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof RadixAddress)) {
			return false;
		}

		RadixAddress other = (RadixAddress) o;
		return other.addressBase58.equals(this.addressBase58);
	}

	@Override
	public int hashCode() {
		return addressBase58.hashCode();
	}
}
