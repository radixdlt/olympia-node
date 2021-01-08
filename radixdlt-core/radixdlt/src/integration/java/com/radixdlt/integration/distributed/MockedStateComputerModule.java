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

package com.radixdlt.integration.distributed;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.bft.VerifiedVertexStoreState;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;

import com.radixdlt.ledger.StateComputerLedger.StateComputerResult;
import com.radixdlt.ledger.StateComputerLedger.PreparedCommand;
import com.radixdlt.ledger.VerifiedCommandsAndProof;

public class MockedStateComputerModule extends AbstractModule {
	@Provides
	private StateComputer stateComputer(Hasher hasher) {
		return new StateComputer() {
			@Override
			public StateComputerResult prepare(
				ImmutableList<PreparedCommand> previous,
				Command next,
				long epoch,
				View view,
				long timestamp
			) {
				return new StateComputerResult(
					next == null
						? ImmutableList.of()
						: ImmutableList.of(new MockPrepared(next, hasher.hash(next))),
					ImmutableMap.of()
				);
			}

			@Override
			public void commit(VerifiedCommandsAndProof command, VerifiedVertexStoreState vertexStoreState) {
				// No op
			}
		};
	}
}
