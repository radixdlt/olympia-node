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

package com.radixdlt.integration.distributed;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.forks.EpochMapKey;
import com.radixdlt.statecomputer.forks.ForkConfig;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MockedRadixEngineForksModule extends AbstractModule {
	private static final long INITIAL_VIEW_CEILING = 10L;

	@Provides
	@Singleton
	private TreeMap<Long, ForkConfig> epochToForkConfig(Map<EpochMapKey, ForkConfig> forkConfigs) {
		var epoch = new AtomicLong(0);
		var viewCeiling = new AtomicLong(INITIAL_VIEW_CEILING);
		return new TreeMap<>(
			forkConfigs.entrySet()
				.stream()
				.collect(
					Collectors.toMap(
						e -> epoch.getAndAdd(5),
						e -> new ForkConfig(e.getValue().getConstraintMachine(), View.of(viewCeiling.getAndAdd(-5))
		 			)
				))
		);
	}

	@Provides
	@Singleton
	@EpochCeilingView
	private View epochCeilingHighView() {
		return View.of(INITIAL_VIEW_CEILING);
	}

	@Provides
	@Singleton
	private ConstraintMachine buildConstraintMachine(
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		return epochToForkConfig.get(0L).getConstraintMachine();
	}
}
