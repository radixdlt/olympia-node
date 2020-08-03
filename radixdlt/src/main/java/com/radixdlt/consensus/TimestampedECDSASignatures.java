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

package com.radixdlt.consensus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * A collection of <a href="https://en.wikipedia.org/wiki/
 * Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a> signatures,
 * together with vote timestamps.
 * <p>
 * Note that the timestamps can be used together with the
 * {@link VoteData} in a {@link QuorumCertificate} to reconstruct
 * {@link TimestampedVoteData} in order to validate signatures.
 */
@Immutable
@SerializerId2("consensus.timestamped_ecdsa_signatures")
public final class TimestampedECDSASignatures {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private ImmutableMap<BFTNode, Pair<Long, ECDSASignature>> keyToTimestampAndSignature;

	@JsonCreator
	public static TimestampedECDSASignatures from(@JsonProperty("signatures") Map<String, Map<Long, ECDSASignature>> signatures) {
		ImmutableMap<BFTNode, Pair<Long, ECDSASignature>> sigs = signatures == null
			? ImmutableMap.of()
			: signatures.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> toBFTNode(e.getKey()), e -> pairFromMap(e.getValue())));
		return new TimestampedECDSASignatures(sigs);
	}

	/**
	 * Returns a new empty instance.
	 */
	public TimestampedECDSASignatures() {
		this.keyToTimestampAndSignature = ImmutableMap.of();
	}

	/**
	 * Returns a new instance containing {@code keyToTimestampAndSignature}.
	 * @param keyToTimestampAndSignature The map of {@link ECDSASignature}s and their corresponding
	 * 		timestamps and {@link ECPublicKey}
	 */
	public TimestampedECDSASignatures(ImmutableMap<BFTNode, Pair<Long, ECDSASignature>> keyToTimestampAndSignature) {
		this.keyToTimestampAndSignature = keyToTimestampAndSignature;
	}

	/**
	 * Returns signatures and timestamps for each public key
	 * @return Signatures and timestamps for each public key
	 */
	public Map<BFTNode, Pair<Long, ECDSASignature>> getSignatures() {
		return this.keyToTimestampAndSignature;
	}

	/**
	 * Returns the count of signatures.
	 * @return The count of signatures
	 */
	public int count() {
		return this.keyToTimestampAndSignature.size();
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.keyToTimestampAndSignature);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TimestampedECDSASignatures)) {
			return false;
		}
		TimestampedECDSASignatures that = (TimestampedECDSASignatures) o;
		return Objects.equals(this.keyToTimestampAndSignature, that.keyToTimestampAndSignature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.keyToTimestampAndSignature);
	}

	@JsonProperty("signatures")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, Map<Long, ECDSASignature>> getSerializerSignatures() {
		if (this.keyToTimestampAndSignature != null) {
			return this.keyToTimestampAndSignature.entrySet().stream()
				.collect(Collectors.toMap(e -> encodePublicKey(e.getKey()), e -> mapFromPair(e.getValue())));
		}
		return null;
	}

	private static String encodePublicKey(BFTNode key) {
		return Bytes.toHexString(key.getKey().getBytes());
	}

	private static BFTNode toBFTNode(String str) {
		try {
			return BFTNode.create(new ECPublicKey(Bytes.fromHexString(str)));
		} catch (CryptoException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}

	private static Pair<Long, ECDSASignature> pairFromMap(Map<Long, ECDSASignature> m) {
		if (m.size() != 1) {
			throw new IllegalArgumentException("Map has incorrect number of entries: " + m);
		}
		Map.Entry<Long, ECDSASignature> entry = m.entrySet().iterator().next();
		return Pair.of(entry.getKey(), entry.getValue());
	}

	private static Map<Long, ECDSASignature> mapFromPair(Pair<Long, ECDSASignature> p) {
		return ImmutableMap.of(p.getFirst(), p.getSecond());
	}
}