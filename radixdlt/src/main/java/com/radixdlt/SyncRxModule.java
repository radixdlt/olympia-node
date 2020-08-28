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
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.SyncServiceRunner.LocalSyncRequestsRx;
import com.radixdlt.sync.SyncServiceRunner.VersionUpdatesRx;
import com.radixdlt.utils.SenderToRx;
import com.radixdlt.utils.TwoSenderToRx;

/**
 * Module which handles message passing to the sync services
 */
public class SyncRxModule extends AbstractModule {
	@Override
	protected void configure() {
		SenderToRx<LocalSyncRequest, LocalSyncRequest> syncRequests = new SenderToRx<>(c -> c);
		bind(SyncRequestSender.class).toInstance(syncRequests::send);
		bind(LocalSyncRequestsRx.class).toInstance(syncRequests::rx);

		TwoSenderToRx<CommittedCommand, BFTValidatorSet, Long> committedCommands
			= new TwoSenderToRx<>((cmd, vset) -> cmd.getVertexMetadata().getPreparedCommand().getStateVersion());
		Multibinder<CommittedSender> committedSenderBinder = Multibinder.newSetBinder(binder(), CommittedSender.class);
		committedSenderBinder.addBinding().toInstance(committedCommands::send);
		bind(VersionUpdatesRx.class).toInstance(committedCommands::rx);
	}
}
