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

package com.radixdlt.integration.distributed.simulation.monitors.ledger;

import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.ledger.LedgerUpdate;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ledger-side safety check. Checks that commands and the order getting persisted are
 * the same across all nodes.
 */
public class LedgerInOrderInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		Map<BFTNode, List<Txn>> commandsPerNode = new HashMap<>();
		network.getNodes().forEach(n -> commandsPerNode.put(n, new ArrayList<>()));

		return network.ledgerUpdates().flatMap(nodeAndCommand -> {
			BFTNode node = nodeAndCommand.getFirst();
			LedgerUpdate ledgerUpdate = nodeAndCommand.getSecond();
			List<Txn> nodeTxns = commandsPerNode.get(node);
			nodeTxns.addAll(ledgerUpdate.getNewTxns());

			return commandsPerNode.values().stream()
				.filter(list -> nodeTxns != list)
				.filter(list -> list.size() >= nodeTxns.size())
				.findFirst() // Only need to check one node, if passes, guaranteed to pass the others
				.flatMap(list -> {
					if (Collections.indexOfSubList(list, nodeTxns) != 0) {
						TestInvariantError err = new TestInvariantError(
							"Two nodes don't agree on commands: " + list + " " + nodeTxns
						);
						return Optional.of(Observable.just(err));
					}
					return Optional.empty();
				})
				.orElse(Observable.empty());
		});
	}
}
