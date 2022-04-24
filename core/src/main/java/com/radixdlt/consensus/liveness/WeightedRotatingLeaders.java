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

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.utils.KeyComparator;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;
import com.radixdlt.utils.UInt384;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Rotates leaders with those having more power being proposed more often in proportion to the
 * amount of power they have.
 *
 * <p>Calculation of the next leader is dependent on the weight state of the previous view and thus
 * computing the leader for an arbitrary view can be quite expensive.
 *
 * <p>We resolve this by keeping a cache of some given size of the previous views closest to the
 * highest view calculated.
 *
 * <p>This class stateful and is NOT thread-safe.
 */
public final class WeightedRotatingLeaders implements ProposerElection {
  private static final int DEFAULT_CACHE_SIZE = 10;
  private static final UInt384 POW_2_256 = UInt384.from(UInt256.MAX_VALUE).increment();

  private final BFTValidatorSet validatorSet;
  private final Comparator<Entry<BFTValidator, UInt384>> weightsComparator;
  private final CachingNextLeaderComputer nextLeaderComputer;

  public WeightedRotatingLeaders(BFTValidatorSet validatorSet) {
    this(validatorSet, DEFAULT_CACHE_SIZE);
  }

  public WeightedRotatingLeaders(BFTValidatorSet validatorSet, int cacheSize) {
    this.validatorSet = validatorSet;
    this.weightsComparator =
        Comparator.comparing(Entry<BFTValidator, UInt384>::getValue)
            .thenComparing(v -> v.getKey().getNode().getKey(), KeyComparator.instance().reversed());
    this.nextLeaderComputer =
        new CachingNextLeaderComputer(validatorSet, weightsComparator, cacheSize);
  }

  private static class CachingNextLeaderComputer {
    private final BFTValidatorSet validatorSet;
    private final Comparator<Entry<BFTValidator, UInt384>> weightsComparator;
    private final Map<BFTValidator, UInt384> weights;
    private final BFTValidator[] cache;
    private final Long lcm;
    private View curView;

    private CachingNextLeaderComputer(
        BFTValidatorSet validatorSet,
        Comparator<Entry<BFTValidator, UInt384>> weightsComparator,
        int cacheSize) {
      this.validatorSet = validatorSet;
      this.weightsComparator = weightsComparator;
      this.weights = new HashMap<>();
      this.cache = new BFTValidator[cacheSize];

      UInt256[] powerArray =
          validatorSet.getValidators().stream().map(BFTValidator::getPower).toArray(UInt256[]::new);
      // after cappedLCM is executed, the following invariant will be true:
      // (lcm > 0 && lcm < 2^63 -1 ) || lcm == null
      // This is due to use of 2^63 - 1 cap and also the invariant from ValidatorSet
      // that powerArray will always be non-zero
      UInt256 lcm256 = UInt256s.cappedLCM(UInt256.from(Long.MAX_VALUE), powerArray);
      this.lcm = lcm256 == null ? null : lcm256.getLow().getLow();

      this.resetToView(View.of(0));
    }

    private BFTValidator computeHeaviest() {
      final Entry<BFTValidator, UInt384> max =
          weights.entrySet().stream()
              .max(weightsComparator)
              .orElseThrow(() -> new IllegalStateException("Weights cannot be empty"));
      return max.getKey();
    }

    private void computeNext() {
      // Reset current leader by subtracting total power
      final int curIndex = (int) (this.curView.number() % cache.length);
      final BFTValidator curLeader = cache[curIndex];
      weights.merge(curLeader, UInt384.from(validatorSet.getTotalPower()), UInt384::subtract);

      // Add weights relative to each validator's power
      for (BFTValidator validator : validatorSet.getValidators()) {
        weights.merge(validator, UInt384.from(validator.getPower()), UInt384::add);
      }

      // Compute next leader by getting heaviest validator
      this.curView = this.curView.next();
      int index = (int) (this.curView.number() % cache.length);
      cache[index] = computeHeaviest();
    }

    private BFTValidator checkCacheForProposer(View view) {
      if (view.compareTo(curView) <= 0 && view.number() > curView.number() - cache.length) {
        final int index = (int) (view.number() % cache.length);
        return cache[index];
      }

      return null;
    }

    private void computeToView(View view) {
      while (view.compareTo(curView) > 0) {
        computeNext();
      }
    }

    private BFTValidator resetToView(View view) {
      // reset if view isn't in cache
      if (curView == null || view.number() < curView.number() - cache.length) {
        if (lcm == null || lcm > view.number()) {
          curView = View.genesis();
        } else {
          long multipleOfLCM = view.number() / lcm;
          curView = View.of(multipleOfLCM * lcm);
        }

        for (BFTValidator validator : validatorSet.getValidators()) {
          weights.put(validator, POW_2_256.subtract(validator.getPower()));
        }
        cache[0] = computeHeaviest();
      }

      // compute to view
      computeToView(view);

      // guaranteed to return non-null;
      return cache[(int) (view.number() % cache.length)];
    }

    @Override
    public String toString() {
      return String.format("%s %s %s", this.curView, Arrays.toString(this.cache), this.weights);
    }
  }

  @Override
  public BFTNode getProposer(View view) {
    nextLeaderComputer.computeToView(view);

    // validator will only be null if the view supplied is before the cache
    // window
    BFTValidator validator = nextLeaderComputer.checkCacheForProposer(view);
    if (validator != null) {
      // dynamic program cache successful
      return validator.getNode();
    } else {
      // cache doesn't have value, do the expensive operation
      CachingNextLeaderComputer computer =
          new CachingNextLeaderComputer(validatorSet, weightsComparator, 1);
      return computer.resetToView(view).getNode();
    }
  }

  @Override
  public String toString() {
    return String.format("%s %s", this.getClass().getSimpleName(), this.nextLeaderComputer);
  }
}
