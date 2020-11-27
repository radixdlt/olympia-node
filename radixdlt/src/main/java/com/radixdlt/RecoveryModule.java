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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.store.berkeley.BerkeleySafetyStateStore;
import java.util.Optional;

/**
 * Manages consensus recovery on restarts
 */
public class RecoveryModule extends AbstractModule {
	@Provides
	@Singleton
	private SafetyState safetyState(EpochChange initialEpoch, BerkeleySafetyStateStore berkeleySafetyStore) {
		return berkeleySafetyStore.get().flatMap(safetyState -> {
			final long safetyStateEpoch =
				safetyState.getLastVote().map(Vote::getEpoch).orElse(0L);

			if (safetyStateEpoch > initialEpoch.getEpoch()) {
				throw new IllegalStateException("Last vote is in a future epoch.");
			} else if (safetyStateEpoch == initialEpoch.getEpoch()) {
				return Optional.of(safetyState);
			} else {
				return Optional.empty();
			}
		}).orElse(new SafetyState());
	}
}
