/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.integration.distributed.simulation.application;

import com.google.inject.Inject;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.SimulationTest;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Register and unregisters nodes as validators randomly
 */
public final class NodeValidatorRandomRegistrator implements SimulationTest.SimulationNetworkActor {
	private Disposable disposable;
	private final Random random;

	@Inject
	public NodeValidatorRandomRegistrator(Random random) {
		this.random = random;
	}

	@Override
	public void start(SimulationNodes.RunningNetwork network) {
		List<BFTNode> nodes = network.getNodes();
		this.disposable = Observable.interval(1, 1, TimeUnit.SECONDS)
			.map(i -> nodes.get(random.nextInt(nodes.size())))
			.subscribe(node -> {
				var d = network.getDispatcher(NodeApplicationRequest.class, node);
				var txnConstructionRequest = TxnConstructionRequest.create();
				if (random.nextBoolean()) {
					txnConstructionRequest.registerAsValidator(node.getKey(), Optional.empty());
				} else {
					txnConstructionRequest.unregisterAsValidator(node.getKey());
				}

				var request = NodeApplicationRequest.create(txnConstructionRequest);
				d.dispatch(request);
			});
	}

	@Override
	public void stop() {
		this.disposable.dispose();
	}
}
