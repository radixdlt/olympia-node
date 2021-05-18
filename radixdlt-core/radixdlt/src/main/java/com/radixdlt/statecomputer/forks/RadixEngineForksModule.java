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

package com.radixdlt.statecomputer.forks;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.sync.CommittedReader;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Module responsible for creating TreeMap of epochs to Fork configuration
 * for use in the RadixEngine
 */
public final class RadixEngineForksModule extends AbstractModule {
	@Provides
	@Singleton
	private TreeMap<Long, ForkConfig> epochToForkConfig(Map<EpochMapKey, ForkConfig> forkConfigs) {
		return new TreeMap<>(
			forkConfigs.entrySet()
				.stream()
				.collect(Collectors.toMap(e -> e.getKey().epoch(), Map.Entry::getValue))
		);
	}

	@Provides
	@Singleton
	@EpochCeilingView
	private View epochCeilingHighView(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getEpochCeilingView();
	}

	@Provides
	@Singleton
	private ConstraintMachine buildConstraintMachine(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getConstraintMachine();
	}


	@Provides
	@Singleton
	private ActionConstructors actionConstructors(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getActionConstructors();
	}
}
