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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt256s;
import com.radixdlt.utils.UInt384;
import java.util.Arrays;
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
		this.weightsComparator = Comparator
			.comparing(Entry<BFTValidator, UInt384>::getValue)
			.thenComparing(
				(o1, o2) -> Arrays.compare(o1.getKey().getNode().getKey().getCompressedBytes(), o2.getKey().getNode().getKey().getCompressedBytes())
			);
		this.nextLeaderComputer = new CachingNextLeaderComputer(validatorSet, weightsComparator, cacheSize);
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
			int cacheSize
		) {
			this.validatorSet = validatorSet;
			this.weightsComparator = weightsComparator;
			this.weights = new HashMap<>();
			this.cache = new BFTValidator[cacheSize];

			UInt256[] powerArray = validatorSet.getValidators().stream().map(BFTValidator::getPower).toArray(UInt256[]::new);
			// after cappedLCM is executed, the following invariant will be true:
			// (lcm > 0 && lcm < 2^63 -1 ) || lcm == null
			// This is due to use of 2^63 - 1 cap and also the invariant from ValidatorSet
			// that powerArray will always be non-zero
			UInt256 lcm256 = UInt256s.cappedLCM(UInt256.from(Long.MAX_VALUE), powerArray);
			this.lcm = lcm256 == null ? null : lcm256.getLow().getLow();

			this.resetToView(View.of(0));
		}

		private BFTValidator computeHeaviest() {
			final Entry<BFTValidator, UInt384> max = weights.entrySet().stream()
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
			CachingNextLeaderComputer computer = new CachingNextLeaderComputer(validatorSet, weightsComparator, 1);
			return computer.resetToView(view).getNode();
		}
	}

	@Override
	public String toString() {
		return String.format("%s %s", this.getClass().getSimpleName(), this.nextLeaderComputer);
	}
}
