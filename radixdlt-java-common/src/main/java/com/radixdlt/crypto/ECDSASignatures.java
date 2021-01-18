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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * A collection of <a href="https://en.wikipedia.org/wiki/
 * Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a> signatures.
 */
@Immutable
@SerializerId2("crypto.ecdsa_signatures")
public final class ECDSASignatures implements Signatures {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private ImmutableMap<ECPublicKey, ECDSASignature> keyToSignature;

	public ECDSASignatures() {
		this.keyToSignature = ImmutableMap.of();
	}

	public ECDSASignatures(ECPublicKey publicKey, ECDSASignature signature) {
		this.keyToSignature = ImmutableMap.of(publicKey, signature);
	}

	/**
	 * Returns a new instance containing {@code keyToSignature}.
	 * @param keyToSignature The map of {@link ECDSASignature}s and their corresponding {@link ECPublicKey}
	 */
	public ECDSASignatures(ImmutableMap<ECPublicKey, ECDSASignature> keyToSignature) {
		this.keyToSignature = keyToSignature;
	}

	@Override
	public SignatureScheme signatureScheme() {
		return SignatureScheme.ECDSA;
	}

	@Override
	public boolean isEmpty() {
		return this.keyToSignature.isEmpty();
	}

	@Override
	public int count() {
		return this.keyToSignature.size();
	}

	@Override
	public Signatures concatenate(ECPublicKey publicKey, Signature signature) {
		if (!(signature instanceof ECDSASignature)) {
			throw new IllegalArgumentException(
					String.format("Expected 'signature' to be of type '%s' but got '%s'",
							ECDSASignature.class.getName(), signature.getClass().getName()
					)
			);
		}
		ImmutableMap.Builder<ECPublicKey, ECDSASignature> builder = ImmutableMap.builder();
		builder.putAll(this.keyToSignature);
		builder.put(publicKey, (ECDSASignature) signature);
		return new ECDSASignatures(builder.build());
	}

	@Override
	public List<ECPublicKey> signedMessage(HashCode message) {
		return this.keyToSignature.entrySet().stream()
				.filter(e -> e.getKey().verify(message, e.getValue()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.keyToSignature);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ECDSASignatures)) {
			return false;
		}
		ECDSASignatures that = (ECDSASignatures) o;
		return Objects.equals(keyToSignature, that.keyToSignature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(keyToSignature);
	}

	@JsonProperty("signatures")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, ECDSASignature> getSerializerSignatures() {
		if (this.keyToSignature != null) {
			return this.keyToSignature.entrySet().stream()
					.collect(Collectors.toMap(e -> encodePublicKey(e.getKey()), Map.Entry::getValue));
		}
		return null;
	}

	@JsonProperty("signatures")
	private void setSerializerSignatures(Map<String, ECDSASignature> signatures) {
		if (signatures != null) {
			this.keyToSignature = signatures.entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(e -> decodePublicKey(e.getKey()), Map.Entry::getValue));
		} else {
			this.keyToSignature = null;
		}
	}

	private String encodePublicKey(ECPublicKey key) {
		return Bytes.toHexString(key.getBytes());
	}

	private ECPublicKey decodePublicKey(String str) {
		try {
			return ECPublicKey.fromBytes(Bytes.fromHexString(str));
		} catch (PublicKeyException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}
}