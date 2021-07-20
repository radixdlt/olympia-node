/* Copyright 2021 Radix DLT Ltd incorporated in England.
 * 
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 * 
 * radixfoundation.org/licenses/LICENSE-v1
 * 
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 * 
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 * 
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 * 
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system 
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 * 
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 * 
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 * 
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 * 
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 * 
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 * 
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 * 
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
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