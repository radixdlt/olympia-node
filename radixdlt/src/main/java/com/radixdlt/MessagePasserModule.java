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
import com.radixdlt.api.LedgerRx;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.middleware2.InternalMessagePasser;
import com.radixdlt.syncer.EpochChangeSender;
import com.radixdlt.syncer.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.syncer.SyncedRadixEngine.SyncedRadixEngineEventSender;
import com.radixdlt.systeminfo.InfoRx;

public class MessagePasserModule extends AbstractModule {

	@Override
	protected void configure() {
		// Local messages
		bind(LedgerRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeRx.class).to(InternalMessagePasser.class);
		bind(EpochInfoSender.class).to(InternalMessagePasser.class);
		bind(InfoRx.class).to(InternalMessagePasser.class);
		bind(SubmissionControlSender.class).to(InternalMessagePasser.class);
		bind(SubmissionErrorsRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeSender.class).to(InternalMessagePasser.class);
		bind(SyncedRadixEngineEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventSender.class).to(InternalMessagePasser.class);
	}

	@Provides
	@Singleton
	private InternalMessagePasser internalMessagePasser() {
		return new InternalMessagePasser();
	}
}
