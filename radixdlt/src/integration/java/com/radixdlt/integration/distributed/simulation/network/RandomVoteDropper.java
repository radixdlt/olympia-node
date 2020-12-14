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

package com.radixdlt.integration.distributed.simulation.network;

import com.radixdlt.consensus.Vote;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.MessageInTransit;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Drops random vote messages
 */
public class RandomVoteDropper implements Predicate<MessageInTransit> {
	private final Random random;
	private final double drops;

	public RandomVoteDropper(Random random, double drops) {
		this.random = Objects.requireNonNull(random);
		this.drops = drops;
	}

	@Override
	public boolean test(MessageInTransit msg) {
		Object content = msg.getContent();
		if (content instanceof Vote) {
			return random.nextDouble() < drops;
		}

		return false;
	}
}
