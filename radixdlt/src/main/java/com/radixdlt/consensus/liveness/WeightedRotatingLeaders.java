/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.liveness;

import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.Validator;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.MathUtils;
import com.radixdlt.utils.UInt128;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Rotates leaders with those having more power being proposed more often
 * in proportion to the amount of power they have.
 *
 * Calculation of the next leader is dependent on the weight state of the
 * previous view and thus computing the leader for an arbitrary view can
 * be quite expensive.
 *
 * We resolve this by keeping a cache of some given size of the previous
 * views closest to the highest view calculated.
 *
 * This class stateful and is NOT thread-safe.
 */
public final class WeightedRotatingLeaders implements ProposerElection {

	private final ValidatorSet validatorSet;
	private final Comparator<Entry<Validator, UInt256>> weightsComparator;
	private final CachingNextLeaderComputer nextLeaderComputer;

	public WeightedRotatingLeaders(ValidatorSet validatorSet, Comparator<Validator> comparator, int cacheSize) {
		this.validatorSet = validatorSet;
		this.weightsComparator = Comparator
			.comparing(Entry<Validator, UInt256>::getValue)
			.thenComparing(Entry::getKey, comparator);
		this.nextLeaderComputer = new CachingNextLeaderComputer(validatorSet, weightsComparator, cacheSize);
	}

	private static class CachingNextLeaderComputer {
		private final ValidatorSet validatorSet;
		private final Comparator<Entry<Validator, UInt256>> weightsComparator;
		private final Map<Validator, UInt256> weights;
		private final Validator[] cache;
		private final UInt128[] powerArray;
		private View curView;

		private CachingNextLeaderComputer(ValidatorSet validatorSet, Comparator<Entry<Validator, UInt256>> weightsComparator, int cacheSize) {
			this.validatorSet = validatorSet;
			this.weightsComparator = weightsComparator;
			this.weights = new HashMap<>();
			this.cache = new Validator[cacheSize];
			this.powerArray = validatorSet.getValidators().stream().map(Validator::getPower).toArray(UInt128[]::new);
			this.resetToView(View.of(0));
		}

		private Validator computeHeaviest() {
			final Entry<Validator, UInt256> max = weights.entrySet().stream()
				.max(weightsComparator)
				.orElseThrow(() -> new IllegalStateException("Weights cannot be empty"));
			return max.getKey();
		}

		private void computeNext() {
			// Reset current leader by subtracting total power
			final int curIndex = (int) (this.curView.number() % cache.length);
			final Validator curLeader = cache[curIndex];
			weights.merge(curLeader, validatorSet.getTotalPower(), UInt256::subtract);

			// Add weights relative to each validator's power
			for (Validator validator : validatorSet.getValidators()) {
				weights.merge(validator, UInt256.from(validator.getPower()), UInt256::add);
			}

			// Compute next leader by getting heaviest validator
			this.curView = this.curView.next();
			int index = (int) (this.curView.number() % cache.length);
			cache[index] = computeHeaviest();
		}

		private Validator checkCacheForProposer(View view) {
			if (view.compareTo(curView) <= 0 && view.number() > curView.number() - cache.length) {
				return cache[(int) (view.number() % cache.length)];
			}

			return null;
		}

		private void computeToView(View view) {
			while (view.compareTo(curView) > 0) {
				computeNext();
			}
		}

		private Validator resetToView(View view) {
			// reset if view isn't in cache
			if (curView == null || view.number() < curView.number() - cache.length) {
				// after cappedLCM is executed, the following invariant will be true:
				// (lcm > 0 && lcm < 2^64) || lcm == null
				// This is due to use of 2^64 cap and also the invariant from ValidatorSet
				// that powerArray will always be non-zero
				UInt128 lcm = MathUtils.cappedLCM(UInt128.from(view.number()), powerArray);
				if (lcm == null) {
					curView = View.of(0);
				} else {
					long lcmLong = lcm.getLow();
					long multipleOfLCM = view.number() / lcmLong;
					curView = View.of(multipleOfLCM * lcmLong);
				}

				for (Validator validator : validatorSet.getValidators()) {
					weights.put(validator, UInt256.from(UInt128.ONE, UInt128.ZERO).subtract(validator.getPower()));
				}
				cache[0] = computeHeaviest();
			}

			// compute to view
			computeToView(view);

			// guaranteed to return non-null;
			return cache[(int) (view.number() % cache.length)];
		}
	}

	@Override
	public ECPublicKey getProposer(View view) {
		nextLeaderComputer.computeToView(view);

		// validator will only be null if the view supplied is before the cache
		// window
		Validator validator = nextLeaderComputer.checkCacheForProposer(view);
		if (validator != null) {
			// dynamic program cache successful
			return validator.nodeKey();
		} else {
			// cache doesn't have value, do the expensive operation
			CachingNextLeaderComputer computer = new CachingNextLeaderComputer(validatorSet, weightsComparator, 1);
			return computer.resetToView(view).nodeKey();
		}
	}
}
