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

package com.radixdlt.integration.distributed.simulation.application;

import com.google.common.collect.ImmutableList;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;
import java.util.Random;

public class EpochsNodeSelector implements NodeSelector {
	private final Random random = new Random();

	@Override
	public Observable<BFTNode> nextNode(RunningNetwork network) {
		return network.latestEpochChanges()
			.map(e -> {
				ImmutableList<BFTValidator> validators = e.getBFTConfiguration()
					.getValidatorSet().getValidators().asList();
				int validatorSetSize = validators.size();
				BFTValidator validator = validators.get(random.nextInt(validatorSetSize));
				return validator.getNode();
			});
	}
}
