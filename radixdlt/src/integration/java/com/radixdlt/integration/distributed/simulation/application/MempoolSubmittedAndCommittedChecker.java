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

import com.radixdlt.consensus.Command;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.systeminfo.InfoRx;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MempoolSubmittedAndCommittedChecker implements TestInvariant {
	private final Observable<Command> submittedCommands;

	public MempoolSubmittedAndCommittedChecker(Observable<Command> submittedCommands) {
		this.submittedCommands = Objects.requireNonNull(submittedCommands);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		Set<Observable<CommittedCommand>> allLedgers
			= network.getNodes().stream().map(network::getInfo).map(InfoRx::committedCommands).collect(Collectors.toSet());

		return submittedCommands
			.flatMapMaybe(command -> {
				List<Maybe<TestInvariantError>> errors = allLedgers.stream()
					.map(cmds -> cmds
						.filter(cmd -> Objects.equals(cmd.getCommand(), command))
						.timeout(10, TimeUnit.SECONDS)
						.firstOrError()
						.ignoreElement()
						.onErrorReturn(e -> new TestInvariantError(e.getMessage() + " " + command))
					)
					.collect(Collectors.toList());
				return Maybe.merge(errors).firstElement();
			});
	}

}
