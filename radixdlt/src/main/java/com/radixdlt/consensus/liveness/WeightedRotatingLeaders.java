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
import com.radixdlt.utils.UInt128;
import com.radixdlt.utils.UInt256;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Rotates leaders with those having more power being proposed more often
 * in proportion to the amount of power they have.
 *
 * This class stateful and is NOT thread-safe.
 */
public final class WeightedRotatingLeaders implements ProposerElection {
	private final Map<View, Validator> leaders = new HashMap<>();
	private final ValidatorSet validatorSet;
	private final Map<Validator, UInt256> weights;
	private final Comparator<Entry<Validator, UInt256>> weightsComparator;
	private View nextView;
	private Validator curLeader;

	WeightedRotatingLeaders(ValidatorSet validatorSet, Comparator<Validator> comparator) {
		this.validatorSet = validatorSet;
		this.weights = validatorSet.getValidators().stream()
			.collect(Collectors.toMap(
				v -> v,
				v -> UInt256.from(UInt128.ONE, UInt128.ZERO).subtract(v.getPower()) // initial round-robin
			));
		this.nextView = View.of(0);
		this.weightsComparator = Comparator
			.comparing(Entry<Validator, UInt256>::getValue)
			.thenComparing(Entry::getKey, comparator);
	}

	private void next() {
		if (curLeader != null) {
			weights.merge(curLeader, validatorSet.getTotalPower(), UInt256::subtract);
		}

		for (Validator validator : validatorSet.getValidators()) {
			weights.merge(validator, validator.getPower(), UInt256::add);
		}

		final Entry<Validator, UInt256> max = weights.entrySet().stream()
			.max(weightsComparator)
			.orElseThrow(() -> new IllegalStateException("Weights cannot be empty"));

		this.curLeader = max.getKey();
		this.leaders.put(this.nextView, this.curLeader);
		this.nextView = this.nextView.next();
	}

	@Override
	public ECPublicKey getProposer(View view) {
		while (view.compareTo(nextView) >= 0) {
			next();
		}

		return leaders.get(view).nodeKey();
	}
}
