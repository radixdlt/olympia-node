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
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.integration.distributed.simulation.TestInvariant;
import com.radixdlt.integration.distributed.simulation.TestInvariant.TestInvariantError;
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

public class RegisteredValidatorChecker implements TestInvariant {
	private final Observable<BFTNode> registeringValidators;

	public RegisteredValidatorChecker(Observable<BFTNode> registeringValidators) {
		this.registeringValidators = Objects.requireNonNull(registeringValidators);
	}

	@Override
	public Observable<TestInvariantError> check(RunningNetwork network) {
		Set<Observable<EpochChange>> allEpochChanges
			= network.getNodes().stream().map(network::getEpochChanges).map(EpochChangeRx::epochChanges).collect(Collectors.toSet());

		return registeringValidators
			.flatMapMaybe(validator -> {
				List<Maybe<TestInvariantError>> errors = allEpochChanges.stream()
					.map(epochChanges -> epochChanges
						.filter(epochChange -> epochChange.getValidatorSet().containsNode(validator))
						.timeout(10, TimeUnit.SECONDS)
						.firstOrError()
						.ignoreElement()
						.onErrorReturn(e -> new TestInvariantError(e.getMessage() + " " + validator))
					)
					.collect(Collectors.toList());
				return Maybe.merge(errors).firstElement();
			});
	}

}
