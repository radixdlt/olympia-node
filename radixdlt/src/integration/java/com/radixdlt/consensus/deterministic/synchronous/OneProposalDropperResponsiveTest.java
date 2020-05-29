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

package com.radixdlt.consensus.deterministic.synchronous;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.radixdlt.consensus.Proposal;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.deterministic.BFTDeterministicTest;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class OneProposalDropperResponsiveTest {

	@Test
	public void when_run_4_correct_nodes_with_channel_order_random_and_timeouts_disabled__then_bft_should_be_responsive() {
		final Cache<View, Integer> proposalToDrop = CacheBuilder.newBuilder()
			.maximumSize(100)
			.build();

		final Random random = new Random(12345);
		final BFTDeterministicTest test = new BFTDeterministicTest(4, true);
		test.start();
		for (int step = 0; step < 100000; step++) {
			test.processNextMsg(random, (receiverId, msg) -> {
				if (msg instanceof Proposal) {
					final Proposal proposal = (Proposal) msg;
					final View view = proposal.getVertex().getView();
					final Integer nodeToDrop;
					try {
						nodeToDrop = proposalToDrop.get(view, () -> random.nextInt(4));
					} catch (ExecutionException e) {
						throw new IllegalStateException();
					}

					return !receiverId.equals(nodeToDrop);
				}

				return true;
			});
		}
	}

}
