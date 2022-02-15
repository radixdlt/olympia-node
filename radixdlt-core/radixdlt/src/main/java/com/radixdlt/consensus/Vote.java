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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashCode;
import com.google.errorprone.annotations.Immutable;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;

/** Represents a vote on a vertex */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Immutable
@SerializerId2("consensus.vote")
public final class Vote implements ConsensusEvent {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(Output.ALL)
  SerializerDummy serializer = SerializerDummy.DUMMY;

  private final BFTNode author;

  @JsonProperty("high_qc")
  @DsonOutput(Output.ALL)
  private final HighQC highQC;

  @JsonProperty("vote_data")
  @DsonOutput(Output.ALL)
  private final VoteData voteData;

  @JsonProperty("ts")
  @DsonOutput(Output.ALL)
  private final long timestamp;

  @JsonProperty("signature")
  @DsonOutput(Output.ALL)
  private final ECDSASignature signature;

  private final Optional<ECDSASignature> timeoutSignature;

  @JsonCreator
  @VisibleForTesting
  public Vote(
      @JsonProperty(value = "author", required = true) byte[] author,
      @JsonProperty(value = "vote_data", required = true) VoteData voteData,
      @JsonProperty("ts") long timestamp,
      @JsonProperty(value = "signature", required = true) ECDSASignature signature,
      @JsonProperty(value = "high_qc", required = true) HighQC highQC,
      @JsonProperty("timeout_signature") ECDSASignature timeoutSignature)
      throws PublicKeyException {
    this(
        BFTNode.fromPublicKeyBytes(requireNonNull(author)),
        voteData,
        timestamp,
        signature,
        highQC,
        Optional.ofNullable(timeoutSignature));
  }

  public Vote(
      BFTNode author,
      VoteData voteData,
      long timestamp,
      ECDSASignature signature,
      HighQC highQC,
      Optional<ECDSASignature> timeoutSignature) {
    if (timestamp <= 0) {
      throw new IllegalArgumentException("Timestamp before epoch:" + timestamp);
    }

    this.author = requireNonNull(author);
    this.voteData = requireNonNull(voteData);
    this.timestamp = timestamp;
    this.signature = requireNonNull(signature);
    this.highQC = requireNonNull(highQC);
    this.timeoutSignature = requireNonNull(timeoutSignature);
  }

  @Override
  public long getEpoch() {
    return voteData.getProposed().getLedgerHeader().getEpoch();
  }

  @Override
  public BFTNode getAuthor() {
    return author;
  }

  @Override
  public HighQC highQC() {
    return this.highQC;
  }

  @Override
  public View getView() {
    return getVoteData().getProposed().getView();
  }

  public VoteData getVoteData() {
    return voteData;
  }

  public static HashCode getHashOfData(Hasher hasher, VoteData voteData, long timestamp) {
    var opaque = hasher.hash(voteData);
    var header = voteData.getCommitted().map(BFTHeader::getLedgerHeader).orElse(null);
    return ConsensusHasher.toHash(opaque, header, timestamp, hasher);
  }

  public HashCode getHashOfData(Hasher hasher) {
    return getHashOfData(hasher, this.voteData, this.timestamp);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public ECDSASignature getSignature() {
    return this.signature;
  }

  public Optional<ECDSASignature> getTimeoutSignature() {
    return timeoutSignature;
  }

  public Vote withTimeoutSignature(ECDSASignature timeoutSignature) {
    return new Vote(
        this.author,
        this.voteData,
        this.timestamp,
        this.signature,
        this.highQC,
        Optional.of(timeoutSignature));
  }

  public boolean isTimeout() {
    return timeoutSignature.isPresent();
  }

  @JsonProperty("author")
  @DsonOutput(Output.ALL)
  private byte[] getSerializerAuthor() {
    return this.author == null ? null : this.author.getKey().getBytes();
  }

  @JsonProperty("timeout_signature")
  @DsonOutput(Output.ALL)
  private ECDSASignature getSerializerTimeoutSignature() {
    return this.timeoutSignature.orElse(null);
  }

  @Override
  public String toString() {
    return String.format(
        "%s{epoch=%s view=%s author=%s timeout?=%s %s}",
        getClass().getSimpleName(), getEpoch(), getView(), author, isTimeout(), highQC);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.author,
        this.voteData,
        this.timestamp,
        this.signature,
        this.highQC,
        this.timeoutSignature);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    return (o instanceof Vote other)
        && Objects.equals(this.author, other.author)
        && Objects.equals(this.voteData, other.voteData)
        && this.timestamp == other.timestamp
        && Objects.equals(this.signature, other.signature)
        && Objects.equals(this.highQC, other.highQC)
        && Objects.equals(this.timeoutSignature, other.timeoutSignature);
  }
}
