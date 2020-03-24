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

package com.radixdlt.crypto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Suppliers;
import com.radixdlt.crypto.encryption.ECIES;
import com.radixdlt.crypto.encryption.ECIESException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.Bytes;
import org.bouncycastle.math.ec.ECPoint;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Asymmetric EC public key provider fixed to curve 'secp256k1'
 */
public final class ECPublicKey {
	public static final int	BYTES = 32;

	@JsonValue
	private final byte[] publicKey;

	private final Supplier<EUID> uid = Suppliers.memoize(this::computeUID);

	@JsonCreator
	private static ECPublicKey fromPublicKey(byte[] key) throws CryptoException {
		return new ECPublicKey(key);
	}

	public ECPublicKey(byte[] key) throws CryptoException {
		try {
			validatePublic(key);
			this.publicKey = Arrays.copyOf(key, key.length);
		} catch (CryptoException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new CryptoException(ex);
		}
	}

	private void validatePublic(byte[] publicKey) throws CryptoException {
		if (publicKey == null || publicKey.length == 0) {
			throw new CryptoException("Public key is empty");
		}

		int pubkey0 = publicKey[0] & 0xFF;
		switch (pubkey0) {
		case 2:
		case 3:
			if (publicKey.length != BYTES + 1) {
				throw new CryptoException("Public key is an invalid compressed size");
			}
			break;
		case 4:
			if (publicKey.length != (BYTES * 2) + 1) {
				throw new CryptoException("Public key is an invalid uncompressed size");
			}
			break;
		default:
			throw new CryptoException("Public key is an invalid format");
		}
	}

	@JsonCreator
	public static ECPublicKey fromBase64(String base64) throws CryptoException {
		return new ECPublicKey(Bytes.fromBase64String(base64));
	}

	public EUID euid() {
		return this.uid.get();
	}

	public byte[] getBytes() {
		return this.publicKey;
	}

	public ECPoint getPublicPoint() {
		return ECKeyUtils.spec().getCurve().decodePoint(this.publicKey);
	}

	public boolean verify(Hash hash, ECDSASignature signature) {
		return verify(hash.toByteArray(), signature);
	}

	public boolean verify(byte[] hash, ECDSASignature signature) {
		if (signature == null) {
			return false;
		}

		try {
			return ECKeyUtils.keyHandler.verify(hash, signature, this.publicKey);
		} catch (CryptoException e) {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.getBytes());
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		}
		if (object instanceof ECPublicKey) {
			ECPublicKey other = (ECPublicKey) object;
			return Arrays.equals(other.publicKey, this.publicKey);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), toBase64());
	}

	public String toBase64() {
		return Bytes.toBase64String(this.publicKey);
	}

	private EUID computeUID() {
		return new EUID(Hash.hash256(getBytes()));
	}

	// ### From Client Library ###
	public int length() {
		return publicKey.length;
	}

	public byte[] encrypt(byte[] data) throws ECIESException {
		return ECIES.encrypt(data, this);
	}
}
