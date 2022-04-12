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
import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;

/** Ledger accumulator which gets voted and agreed upon */
@Immutable
@SerializerId2("consensus.ledger_header")
public final class LedgerHeader {
  @JsonProperty(SerializerConstants.SERIALIZER_NAME)
  @DsonOutput(value = {Output.API, Output.WIRE, Output.PERSIST})
  SerializerDummy serializer = SerializerDummy.DUMMY;

  @JsonProperty("epoch")
  @DsonOutput(Output.ALL)
  private final long epoch;

  private final View view;

  @JsonProperty("accumulator_state")
  @DsonOutput(Output.ALL)
  private final AccumulatorState accumulatorState;

  @JsonProperty("timestamp")
  @DsonOutput(Output.ALL)
  private final long timestamp; // TODO: Move into command accumulator

  @JsonProperty("next_validators")
  @DsonOutput(Output.ALL)
  private final ImmutableSet<BFTValidator> nextValidators;

  // TODO: Replace isEndOfEpoch with nextValidatorSet
  @JsonCreator
  @VisibleForTesting
  LedgerHeader(
      @JsonProperty("epoch") long epoch,
      @JsonProperty("view") long view,
      @JsonProperty(value = "accumulator_state", required = true) AccumulatorState accumulatorState,
      @JsonProperty("timestamp") long timestamp,
      @JsonProperty("next_validators") ImmutableSet<BFTValidator> nextValidators) {
    this(epoch, View.of(view), accumulatorState, timestamp, nextValidators);
  }

  private LedgerHeader(
      long epoch,
      View view,
      AccumulatorState accumulatorState,
      long timestamp,
      ImmutableSet<BFTValidator> nextValidators) {
    this.epoch = epoch;

    if (epoch < 0) {
      throw new IllegalArgumentException("Epoch can't be < 0");
    }

    this.view = view;
    this.accumulatorState = requireNonNull(accumulatorState);
    this.nextValidators = nextValidators;
    this.timestamp = timestamp;
  }

  public static LedgerHeader genesis(
      AccumulatorState accumulatorState, BFTValidatorSet nextValidators, long timestamp) {
    return new LedgerHeader(
        0,
        View.genesis(),
        accumulatorState,
        timestamp,
        nextValidators == null ? null : nextValidators.getValidators());
  }

  public static LedgerHeader create(
      long epoch, View view, AccumulatorState accumulatorState, long timestamp) {
    return new LedgerHeader(epoch, view, accumulatorState, timestamp, null);
  }

  public static LedgerHeader create(
      long epoch,
      View view,
      AccumulatorState accumulatorState,
      long timestamp,
      BFTValidatorSet validatorSet) {
    return new LedgerHeader(
        epoch,
        view,
        accumulatorState,
        timestamp,
        validatorSet == null ? null : validatorSet.getValidators());
  }

  public LedgerHeader updateViewAndTimestamp(View view, long timestamp) {
    return new LedgerHeader(
        this.epoch, view, this.accumulatorState, timestamp, this.nextValidators);
  }

  @JsonProperty("view")
  @DsonOutput(Output.ALL)
  private long getSerializerView() {
    return view.number();
  }

  public View getView() {
    return view;
  }

  public Optional<BFTValidatorSet> getNextValidatorSet() {
    return Optional.ofNullable(nextValidators).map(BFTValidatorSet::from);
  }

  public AccumulatorState getAccumulatorState() {
    return accumulatorState;
  }

  public long getEpoch() {
    return epoch;
  }

  public boolean isEndOfEpoch() {
    return nextValidators != null;
  }

  public long timestamp() {
    return this.timestamp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        this.accumulatorState, this.timestamp, this.epoch, this.view, this.nextValidators);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    return (o instanceof LedgerHeader other)
        && this.timestamp == other.timestamp
        && Objects.equals(this.accumulatorState, other.accumulatorState)
        && this.epoch == other.epoch
        && Objects.equals(this.view, other.view)
        && Objects.equals(this.nextValidators, other.nextValidators);
  }

  @Override
  public String toString() {
    return String.format(
        "%s{accumulator=%s timestamp=%s epoch=%s view=%s nextValidators=%s}",
        getClass().getSimpleName(),
        this.accumulatorState,
        this.timestamp,
        this.epoch,
        this.view,
        this.nextValidators);
  }
}
