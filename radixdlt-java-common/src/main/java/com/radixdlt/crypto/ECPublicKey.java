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
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.encryption.ECIES;
import com.radixdlt.crypto.exception.ECIESException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.Bytes;
import org.bouncycastle.math.ec.ECPoint;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Asymmetric EC public key provider fixed to curve 'secp256k1'
 */
public final class ECPublicKey {
    public static final int BYTES = 32;

    @JsonValue
    private final byte[] publicKey;

    private final Supplier<EUID> uid = Suppliers.memoize(this::computeUID);
    private final int hashCode;

    private ECPublicKey(byte[] key) {
        this.publicKey = Arrays.copyOf(key, key.length);
        this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
        return Arrays.hashCode(publicKey);
    }

    @JsonCreator
    public static ECPublicKey fromBytes(byte[] key) throws PublicKeyException {
        ECKeyUtils.validatePublic(key);
        return new ECPublicKey(key);
    }

    @JsonCreator
    public static ECPublicKey fromBase64(String base64) throws PublicKeyException {
        return fromBytes(Bytes.fromBase64String(base64));
    }

    public EUID euid() {
        return this.uid.get();
    }

    public byte[] getBytes() {
        return this.publicKey;
    }

    public int length() {
        return publicKey.length;
    }

    public ECPoint getPublicPoint() {
        return ECKeyUtils.spec().getCurve().decodePoint(this.publicKey);
    }

    public boolean verify(HashCode hash, ECDSASignature signature) {
        return verify(hash.asBytes(), signature);
    }

    public boolean verify(byte[] hash, ECDSASignature signature) {
        return signature != null && ECKeyUtils.keyHandler.verify(hash, signature, this.publicKey);
    }

    public byte[] encrypt(byte[] data) throws ECIESException {
        return ECIES.encrypt(data, this);
    }

    public String toBase64() {
        return Bytes.toBase64String(this.publicKey);
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
            ECPublicKey other = (ECPublicKey) object;
            return this.hashCode() == other.hashCode() && Arrays.equals(this.publicKey, other.publicKey);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), toBase64());
    }

    private EUID computeUID() {
        return EUID.sha256(getBytes());
    }
}
