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

package com.radixdlt.integration.distributed.deterministic.configuration;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.radixdlt.utils.UInt256;

/**
 * Mapping from epoch to validator set.
 */
@FunctionalInterface
public interface EpochNodeWeightMapping {
	Stream<NodeIndexAndWeight> nodesAndWeightFor(long epoch);

	/**
	 * Returns an {@code EpochValidatorSetMapping} of the specified size
	 * and all with the specified weight.
	 */
	static EpochNodeWeightMapping constant(int numNodes, long weight) {
		return repeatingSequence(numNodes, UInt256.from(weight));
	}

	/**
	 * Returns an {@code EpochValidatorSetMapping} of the specified size
	 * and all with the specified weight.
	 */
	static EpochNodeWeightMapping constant(int numNodes, UInt256 weight) {
		return repeatingSequence(numNodes, weight);
	}

	/**
	 * Returns an {@code EpochValidatorSetMapping} of the specified size
	 * and with the specified weights.  If the length of {@code weights} is
	 * less than {@code numNodes}, then the weights are cycled starting from
	 * the zeroth weight.
	 */
	static EpochNodeWeightMapping repeatingSequence(int numNodes, long... weights) {
		UInt256[] weights256 = Arrays.stream(weights)
			.mapToObj(UInt256::from)
			.toArray(UInt256[]::new);
		return repeatingSequence(numNodes, weights256);
	}

	/**
	 * Returns an {@code EpochValidatorSetMapping} of the specified size
	 * and with the specified weights.  If the length of {@code weights} is
	 * less than {@code numNodes}, then the weights are cycled starting from
	 * the zeroth weight.
	 */
	static EpochNodeWeightMapping repeatingSequence(int numNodes, UInt256... weights) {
		int length = weights.length;
		return epoch -> IntStream.range(0, numNodes)
			.mapToObj(index -> NodeIndexAndWeight.from(index, weights[index % length]));
	}

	/**
	 * Returns an {@code EpochValidatorSetMapping} of the specified size
	 * and with each weight computed using the specified function.
	 */
	static EpochNodeWeightMapping computed(int numNodes, IntFunction<UInt256> function) {
		return epoch -> IntStream.range(0, numNodes)
			.mapToObj(index -> NodeIndexAndWeight.from(index, function.apply(index)));
	}
}
