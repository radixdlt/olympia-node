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

package com.radixdlt.integration.distributed.simulation.invariants.ledger;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.ledger.VerifiedCommittedCommand;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.Observable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ledger-side safety check. Checks that commands and the order getting persisted are
 * the same across all nodes.
 */
public class SyncedInOrderInvariant implements TestInvariant {

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		Observable<Pair<BFTNode, VerifiedCommittedCommand>> commands
			= Observable.merge(
				network.getNodes()
					.stream()
					.map(n -> network.getInfo(n).committedCommands().map(c -> Pair.of(n, c)))
					.collect(Collectors.toSet())
		);

		Map<BFTNode, List<VerifiedCommittedCommand>> commandsPerNode = new HashMap<>();
		network.getNodes().forEach(n -> commandsPerNode.put(n, new ArrayList<>()));

		return commands.flatMap(nodeAndCommand -> {
			BFTNode node = nodeAndCommand.getFirst();
			VerifiedCommittedCommand cmd = nodeAndCommand.getSecond();
			List<VerifiedCommittedCommand> nodeCommands = commandsPerNode.get(node);
			nodeCommands.add(cmd);

			return commandsPerNode.values().stream()
				.filter(list -> nodeCommands != list)
				.filter(list -> list.size() >= nodeCommands.size())
				.findFirst() // Only need to check one node, if passes, guaranteed to pass the others
				.flatMap(list -> {
					if (Collections.indexOfSubList(list, nodeCommands) != 0) {
						TestInvariantError err = new TestInvariantError(
							"Two nodes don't agree on commands: " + list + " " + nodeCommands
						);
						return Optional.of(Observable.just(err));
					}
					return Optional.empty();
				})
				.orElse(Observable.empty());
		});
	}
}
