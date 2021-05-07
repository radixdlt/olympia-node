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
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
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
 * {@link ConsensusHasher} in order to validate signatures.
 */
@Immutable
@SerializerId2("consensus.timestamped_ecdsa_signatures")
public final class TimestampedECDSASignatures {
	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	private Map<BFTNode, TimestampedECDSASignature> nodeToTimestampedSignature;

	@JsonCreator
	public static TimestampedECDSASignatures from(@JsonProperty("signatures") Map<String, TimestampedECDSASignature> signatures) {
		var signaturesByNode =
			signatures == null
			? Map.<BFTNode, TimestampedECDSASignature>of()
			: signatures.entrySet().stream().collect(Collectors.toMap(e -> toBFTNode(e.getKey()), Map.Entry::getValue));

		return new TimestampedECDSASignatures(signaturesByNode);
	}

	public JSONArray asJSON() {
		var json = new JSONArray();
		nodeToTimestampedSignature.forEach((node, sig) -> {
			var obj = new JSONObject()
				.put("address", ValidatorAddress.of(node.getKey()))
				.put("signature", sig.signature().toHexString())
				.put("timestamp", sig.timestamp());
			json.put(obj);
		});

		return json;
	}

	/**
	 * Returns a new empty instance.
	 */
	public TimestampedECDSASignatures() {
		this.nodeToTimestampedSignature = Map.of();
	}

	/**
	 * Returns a new instance containing {@code nodeToTimestampAndSignature}.
	 *
	 * @param nodeToTimestampAndSignature The map of {@link com.radixdlt.crypto.ECDSASignature}s and their corresponding
	 * 	timestamps and {@link com.radixdlt.crypto.ECPublicKey}
	 */
	public TimestampedECDSASignatures(Map<BFTNode, TimestampedECDSASignature> nodeToTimestampAndSignature) {
		this.nodeToTimestampedSignature = nodeToTimestampAndSignature;
	}

	/**
	 * Returns signatures and timestamps for each public key
	 *
	 * @return Signatures and timestamps for each public key
	 */
	public Map<BFTNode, TimestampedECDSASignature> getSignatures() {
		return this.nodeToTimestampedSignature;
	}

	/**
	 * Returns the count of signatures.
	 *
	 * @return The count of signatures
	 */
	public int count() {
		return this.nodeToTimestampedSignature.size();
	}

	/**
	 * Returns the weighted timestamp for this set of timestamped signatures.
	 *
	 * @return The weighted timestamp, or {@code Long.MIN_VALUE} if a timestamp cannot be computed
	 */
	public long weightedTimestamp() {
		var totalPower = UInt256.ZERO;
		var weightedTimes = new ArrayList<Pair<Long, UInt256>>();

		for (var ts : this.nodeToTimestampedSignature.values()) {
			var weight = ts.weight();
			totalPower = totalPower.add(weight);
			weightedTimes.add(Pair.of(ts.timestamp(), weight));
		}

		if (totalPower.isZero()) {
			return Long.MIN_VALUE; // Invalid timestamp
		}

		var median = totalPower.shiftRight(); // Divide by 2

		// Sort ascending by timestamp
		weightedTimes.sort(Comparator.comparing(Pair::getFirst));

		for (var w : weightedTimes) {
			var weight = w.getSecond();

			if (median.compareTo(weight) < 0) {
				return w.getFirst();
			}

			median = median.subtract(weight);
		}
		throw new IllegalStateException("Logic error in weightedTimestamp");
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.nodeToTimestampedSignature);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TimestampedECDSASignatures)) {
			return false;
		}
		TimestampedECDSASignatures that = (TimestampedECDSASignatures) o;
		return Objects.equals(this.nodeToTimestampedSignature, that.nodeToTimestampedSignature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.nodeToTimestampedSignature);
	}

	@JsonProperty("signatures")
	@DsonOutput(DsonOutput.Output.ALL)
	private Map<String, TimestampedECDSASignature> getSerializerSignatures() {
		if (this.nodeToTimestampedSignature != null) {
			return this.nodeToTimestampedSignature.entrySet().stream()
				.collect(Collectors.toMap(e -> encodePublicKey(e.getKey()), Map.Entry::getValue));
		}
		return null;
	}

	private static String encodePublicKey(BFTNode key) {
		return Bytes.toHexString(key.getKey().getBytes());
	}

	private static BFTNode toBFTNode(String str) {
		try {
			return BFTNode.fromPublicKeyBytes(Bytes.fromHexString(str));
		} catch (PublicKeyException e) {
			throw new IllegalStateException("Error decoding public key", e);
		}
	}
}