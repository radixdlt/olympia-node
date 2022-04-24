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

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Set of validators for consensus. Only validators with power >= 1 will be part of the set.
 *
 * <p>Note that this set will validate for set sizes less than 4, as long as all validators sign.
 */
public final class BFTValidatorSet {
  private final ImmutableBiMap<BFTNode, BFTValidator> validators;

  // Because we will base power on tokens and because tokens have a max limit
  // of 2^256 this should never overflow
  private final transient UInt256 totalPower;

  private BFTValidatorSet(Collection<BFTValidator> validators) {
    this(validators.stream());
  }

  private BFTValidatorSet(Stream<BFTValidator> validators) {
    this.validators =
        validators
            .filter(v -> !v.getPower().isZero())
            .collect(ImmutableBiMap.toImmutableBiMap(BFTValidator::getNode, Function.identity()));
    this.totalPower =
        this.validators.values().stream()
            .map(BFTValidator::getPower)
            .reduce(UInt256::add)
            .orElse(UInt256.ZERO);
  }

  /**
   * Create a validator set from a collection of validators. The sum of power of all validator
   * should not exceed UInt256.MAX_VALUE otherwise the resulting ValidatorSet will perform in an
   * undefined way. This invariant should be upheld within the system due to max number of tokens
   * being constrained to UInt256.MAX_VALUE.
   *
   * @param validators the collection of validators
   * @return The new {@code ValidatorSet}.
   */
  public static BFTValidatorSet from(Collection<BFTValidator> validators) {
    return new BFTValidatorSet(validators);
  }

  /**
   * Create a validator set from a stream of validators. The sum of power of all validator should
   * not exceed UInt256.MAX_VALUE otherwise the resulting ValidatorSet will perform in an undefined
   * way. This invariant should be upheld within the system due to max number of tokens being
   * constrained to UInt256.MAX_VALUE.
   *
   * @param validators the stream of validators
   * @return The new {@code ValidatorSet}.
   */
  public static BFTValidatorSet from(Stream<BFTValidator> validators) {
    return new BFTValidatorSet(validators);
  }

  /**
   * Create an initial validation state with no signatures for this validator set.
   *
   * @return An initial validation state with no signatures
   */
  public ValidationState newValidationState() {
    return ValidationState.forValidatorSet(this);
  }

  public boolean containsNode(BFTNode node) {
    return validators.containsKey(node);
  }

  public boolean containsNode(ECPublicKey publicKey) {
    return containsNode(BFTNode.create(publicKey));
  }

  public UInt256 getPower(BFTNode node) {
    return validators.get(node).getPower();
  }

  public UInt256 getPower(ECPublicKey publicKey) {
    return getPower(BFTNode.create(publicKey));
  }

  public UInt256 getTotalPower() {
    return totalPower;
  }

  public ImmutableSet<BFTValidator> getValidators() {
    return validators.values();
  }

  public ImmutableSet<BFTNode> nodes() {
    return validators.keySet();
  }

  public ImmutableMap<BFTNode, BFTValidator> validatorsByKey() {
    return validators;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.validators);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof BFTValidatorSet) {
      BFTValidatorSet other = (BFTValidatorSet) obj;
      return Objects.equals(this.validators, other.validators);
    }
    return false;
  }

  @Override
  public String toString() {
    final StringJoiner joiner = new StringJoiner(",");
    for (BFTValidator validator : this.validators.values()) {
      joiner.add(String.format("%s=%s", validator.getNode().getSimpleName(), validator.getPower()));
    }
    return String.format("%s[%s]", this.getClass().getSimpleName(), joiner.toString());
  }
}
