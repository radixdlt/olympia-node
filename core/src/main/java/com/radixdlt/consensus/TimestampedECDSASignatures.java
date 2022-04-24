/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
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

package com.radixdlt.consensus;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.concurrent.Immutable;
import org.json.JSONArray;

/**
 * A collection of <a href="https://en.wikipedia.org/wiki/
 * Elliptic_Curve_Digital_Signature_Algorithm">ECDSA</a> signatures, together with vote timestamps.
 *
 * <p>Note that the timestamps can be used together with the {@link VoteData} in a {@link
 * QuorumCertificate} to reconstruct {@link ConsensusHasher} in order to validate signatures.
 */
@Immutable
@SerializerId2("consensus.timestamped_ecdsa_signatures")
public final class TimestampedECDSASignatures {
  // Placeholder for the serializer ID
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(DsonOutput.Output.ALL)
  private SerializerDummy serializer = SerializerDummy.DUMMY;

  private final Map<BFTNode, TimestampedECDSASignature> nodeToTimestampedSignature;

  @JsonCreator
  public static TimestampedECDSASignatures from(
      @JsonProperty("signatures") Map<String, TimestampedECDSASignature> signatures) {
    if (signatures != null) {
      signatures.forEach(
          (key, value) -> {
            requireNonNull(key);
            requireNonNull(value);
          });
    }

    var signaturesByNode =
        signatures == null
            ? Map.<BFTNode, TimestampedECDSASignature>of()
            : signatures.entrySet().stream()
                .collect(Collectors.toMap(e -> toBFTNode(e.getKey()), Map.Entry::getValue));

    return new TimestampedECDSASignatures(signaturesByNode);
  }

  public static TimestampedECDSASignatures fromJSON(JSONArray json) throws DeserializeException {
    var builder = ImmutableMap.<BFTNode, TimestampedECDSASignature>builder();
    for (int i = 0; i < json.length(); i++) {
      var signatureJson = json.getJSONObject(i);

      try {
        var key = ECPublicKey.fromHex(signatureJson.getString("key"));
        var bytes = Bytes.fromHexString(signatureJson.getString("signature"));
        var signature = REFieldSerialization.deserializeSignature(ByteBuffer.wrap(bytes));
        var timestamp = signatureJson.getLong("timestamp");
        builder.put(BFTNode.create(key), TimestampedECDSASignature.from(timestamp, signature));
      } catch (PublicKeyException e) {
        throw new DeserializeException(e.getMessage());
      }
    }
    return new TimestampedECDSASignatures(builder.build());
  }

  /** Returns a new empty instance. */
  public TimestampedECDSASignatures() {
    this.nodeToTimestampedSignature = Map.of();
  }

  /**
   * Returns a new instance containing {@code nodeToTimestampAndSignature}.
   *
   * @param nodeToTimestampAndSignature The map of {@link com.radixdlt.crypto.ECDSASignature}s and
   *     their corresponding timestamps and {@link com.radixdlt.crypto.ECPublicKey}
   */
  public TimestampedECDSASignatures(
      Map<BFTNode, TimestampedECDSASignature> nodeToTimestampAndSignature) {
    this.nodeToTimestampedSignature =
        nodeToTimestampAndSignature == null ? Map.of() : nodeToTimestampAndSignature;
    this.nodeToTimestampedSignature.forEach(
        (key, value) -> {
          requireNonNull(key);
          requireNonNull(value);
        });
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
      var weight = UInt256.ONE;
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
