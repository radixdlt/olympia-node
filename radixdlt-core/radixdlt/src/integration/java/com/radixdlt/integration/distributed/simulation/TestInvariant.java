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

package com.radixdlt.integration.distributed.simulation;

import com.radixdlt.integration.distributed.simulation.network.SimulationNodes.RunningNetwork;
import io.reactivex.rxjava3.core.Observable;

/**
 * A running BFT check given access to network
 */
public interface TestInvariant {
	class TestInvariantError {
		private final String description;
		public TestInvariantError(String description) {
			this.description = description;
		}

		public String getDescription() {
			return description;
		}

		@Override
		public String toString() {
			return String.format("%s{desc=%s}", this.getClass().getSimpleName(), description);
		}
	}

	/**
	 * Creates an observable which runs assertions against a bft network.
	 * Assertions errors are expected to propagate down the observable.
	 * TODO: Cleanup interface a bit
	 *
	 * @param network network to check
	 * @return completable to subscribe to enable checking
	 */
	Observable<TestInvariantError> check(RunningNetwork network);
}
