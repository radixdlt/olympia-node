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

import com.radixdlt.utils.Base58;
import org.bouncycastle.math.ec.ECPoint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Suppliers;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.encryption.ECIES;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.utils.Bytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Asymmetric EC public key provider fixed to curve 'secp256k1'
 */
public final class ECPublicKey {
	public static final int COMPRESSED_BYTES = 33; // 32 + header byte
	public static final int UNCOMPRESSED_BYTES = 65; // 64 + header byte

	private final ECPoint ecPoint;
	private final Supplier<byte[]> uncompressedBytes;
	private final Supplier<EUID> uid;
	private final int hashCode;

	private ECPublicKey(ECPoint ecPoint) {
		this.ecPoint = Objects.requireNonNull(ecPoint);
		this.uncompressedBytes = Suppliers.memoize(() -> this.ecPoint.getEncoded(false));
		this.uid = Suppliers.memoize(this::computeUID);
		this.hashCode = computeHashCode();
	}

	private int computeHashCode() {
		return Arrays.hashCode(uncompressedBytes.get());
	}

	public static ECPublicKey fromEcPoint(ECPoint ecPoint) {
		return new ECPublicKey(ecPoint);
	}

	@JsonCreator
	public static ECPublicKey fromBytes(byte[] key) throws PublicKeyException {
		ECKeyUtils.validatePublic(key);
		return new ECPublicKey(ECKeyUtils.spec().getCurve().decodePoint(key));
	}

	@JsonCreator
	public static ECPublicKey fromHex(String hex) throws PublicKeyException {
		return fromBytes(Bytes.fromHexString(hex));
	}

	@JsonCreator
	public static ECPublicKey fromBase64(String base64) throws PublicKeyException {
		return fromBytes(Bytes.fromBase64String(base64));
	}

	public static ECPublicKey fromBase58(String base58) throws PublicKeyException {
		return fromBytes(Bytes.fromBase58String(base58));
	}

	public static Optional<ECPublicKey> recoverFrom(HashCode hash, ECDSASignature signature) {
		return ECKeyUtils.recoverFromSignature(signature, hash.asBytes())
			.map(ECPublicKey::new);
	}

	public EUID euid() {
		return this.uid.get();
	}

	public ECPoint getEcPoint() {
		return ecPoint;
	}

	@JsonProperty("publicKey")
	@DsonOutput(DsonOutput.Output.ALL)
	public byte[] getBytes() {
		return this.uncompressedBytes.get();
	}

	public byte[] getCompressedBytes() {
		return this.ecPoint.getEncoded(true);
	}

	public boolean verify(HashCode hash, ECDSASignature signature) {
		return verify(hash.asBytes(), signature);
	}

	public boolean verify(byte[] hash, ECDSASignature signature) {
		return signature != null && ECKeyUtils.keyHandler.verify(hash, signature, ecPoint);
	}

	public byte[] encrypt(byte[] data) throws ECIESException {
		return ECIES.encrypt(data, this);
	}

	public String toBase64() {
		return Bytes.toBase64String(getBytes());
	}

	public String toBase58() {
		return Base58.toBase58(getBytes());
	}

	public String toHex() {
		return Bytes.toHexString(this.getBytes());
	}

	@Override
	public int hashCode() {
		return this.hashCode;
	}

	@Override
	public boolean equals(Object object) {
		if (object == this) {
			return true;
		}
		if (object instanceof ECPublicKey) {
			final var that = (ECPublicKey) object;
			return Arrays.equals(uncompressedBytes.get(), that.uncompressedBytes.get());
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), toHex());
	}

	private EUID computeUID() {
		return EUID.sha256(getCompressedBytes());
	}
}
