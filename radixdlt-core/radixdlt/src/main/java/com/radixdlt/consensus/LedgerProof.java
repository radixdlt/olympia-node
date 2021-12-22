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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/** Ledger header with proof */
@Immutable
@SerializerId2("ledger.proof")
public final class LedgerProof {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
  SerializerDummy serializer = SerializerDummy.DUMMY;

  // proposed
  @JsonProperty("opaque")
  @DsonOutput(Output.ALL)
  private final HashCode opaque;

  // committed ledgerState
  @JsonProperty("ledgerState")
  @DsonOutput(Output.ALL)
  private final LedgerHeader ledgerHeader;

  @JsonProperty("signatures")
  @DsonOutput(Output.ALL)
  private final TimestampedECDSASignatures signatures;

  @JsonCreator
  public LedgerProof(
      @JsonProperty(value = "opaque", required = true) HashCode opaque,
      @JsonProperty(value = "ledgerState", required = true) LedgerHeader ledgerHeader,
      @JsonProperty(value = "signatures", required = true) TimestampedECDSASignatures signatures) {
    this.opaque = Objects.requireNonNull(opaque);
    this.ledgerHeader = Objects.requireNonNull(ledgerHeader);
    this.signatures = Objects.requireNonNull(signatures);
  }

  public static LedgerProof mock() {
    var acc = new AccumulatorState(0, HashUtils.zero256());
    var header = LedgerHeader.create(0, View.genesis(), acc, 0);
    return new LedgerProof(HashUtils.zero256(), header, new TimestampedECDSASignatures());
  }

  public static LedgerProof genesis(
      AccumulatorState accumulatorState, BFTValidatorSet nextValidators, long timestamp) {
    var genesisLedgerHeader = LedgerHeader.genesis(accumulatorState, nextValidators, timestamp);
    return new LedgerProof(
        HashUtils.zero256(), genesisLedgerHeader, new TimestampedECDSASignatures());
  }

  public static final class OrderByEpochAndVersionComparator implements Comparator<LedgerProof> {
    @Override
    public int compare(LedgerProof p0, LedgerProof p1) {
      if (p0.ledgerHeader.getEpoch() != p1.ledgerHeader.getEpoch()) {
        return p0.ledgerHeader.getEpoch() > p1.ledgerHeader.getEpoch() ? 1 : -1;
      }

      if (p0.ledgerHeader.isEndOfEpoch() != p1.ledgerHeader.isEndOfEpoch()) {
        return p0.ledgerHeader.isEndOfEpoch() ? 1 : -1;
      }

      return Long.compare(
          p0.ledgerHeader.getAccumulatorState().getStateVersion(),
          p1.ledgerHeader.getAccumulatorState().getStateVersion());
    }
  }

  public DtoLedgerProof toDto() {
    return new DtoLedgerProof(opaque, ledgerHeader, signatures);
  }

  public LedgerHeader getRaw() {
    return ledgerHeader;
  }

  public Optional<BFTValidatorSet> getNextValidatorSet() {
    return ledgerHeader.getNextValidatorSet();
  }

  public long getEpoch() {
    return ledgerHeader.getEpoch();
  }

  public View getView() {
    return ledgerHeader.getView();
  }

  public AccumulatorState getAccumulatorState() {
    return ledgerHeader.getAccumulatorState();
  }

  // TODO: Remove
  public long getStateVersion() {
    return ledgerHeader.getAccumulatorState().getStateVersion();
  }

  public long timestamp() {
    return ledgerHeader.timestamp();
  }

  public boolean isEndOfEpoch() {
    return ledgerHeader.isEndOfEpoch();
  }

  public TimestampedECDSASignatures getSignatures() {
    return signatures;
  }

  public ImmutableList<BFTNode> getSignersWithout(BFTNode remove) {
    return signatures.getSignatures().keySet().stream()
        .filter(n -> !n.equals(remove))
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public int hashCode() {
    return Objects.hash(opaque, ledgerHeader, signatures);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof LedgerProof)) {
      return false;
    }

    LedgerProof other = (LedgerProof) o;
    return Objects.equals(this.opaque, other.opaque)
        && Objects.equals(this.ledgerHeader, other.ledgerHeader)
        && Objects.equals(this.signatures, other.signatures);
  }

  @Override
  public String toString() {
    return String.format("%s{ledger=%s}", this.getClass().getSimpleName(), this.ledgerHeader);
  }
}
