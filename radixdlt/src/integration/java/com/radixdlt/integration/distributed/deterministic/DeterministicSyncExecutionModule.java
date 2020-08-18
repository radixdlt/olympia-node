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

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.SyncedExecutor;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.integration.distributed.deterministic.configuration.SyncedExecutorFactory;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncExecutor.CommittedStateSyncSender;

import io.reactivex.rxjava3.core.Observable;

public class DeterministicSyncExecutionModule extends AbstractModule {
	private final BFTValidatorSet validatorSet;
	private final SyncedExecutorFactory syncedFactory;

	public DeterministicSyncExecutionModule(BFTValidatorSet validatorSet, SyncedExecutorFactory syncedFactory) {
		this.validatorSet = validatorSet;
		this.syncedFactory = syncedFactory;
	}

	@Override
	public void configure() {
		bind(CommittedStateSyncRx.class).toInstance(Observable::never);
		bind(EpochChangeRx.class).toInstance(Observable::never);
		EpochChange initialEpoch = new EpochChange(VertexMetadata.ofGenesisAncestor(this.validatorSet), validatorSet);
		bind(EpochChange.class).toInstance(initialEpoch);
	}

	@Provides
	@Singleton
	SyncedExecutor syncedExecutor(CommittedStateSyncSender committedSender, EpochChangeSender epochSender) {
		return syncedFactory.createComputer(committedSender, epochSender);
	}
}
