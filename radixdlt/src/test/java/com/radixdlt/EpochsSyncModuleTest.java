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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.epochs.EpochsLedgerUpdate;
import com.radixdlt.epochs.EpochsLocalSyncServiceProcessor;
import com.radixdlt.epochs.EpochsRemoteSyncResponseProcessor;
import com.radixdlt.epochs.SyncedEpochSender;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.sync.LedgerUpdateProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import com.radixdlt.sync.StateSyncNetwork;
import java.util.Comparator;
import org.junit.Test;

public class EpochsSyncModuleTest {
	@Inject
	private SyncedEpochSender syncedEpochSender;

	@Inject
	private LedgerUpdateProcessor<EpochsLedgerUpdate> updateProcessor;

	private EpochsRemoteSyncResponseProcessor remoteSyncResponseProcessor = mock(EpochsRemoteSyncResponseProcessor.class);
	private EpochsLocalSyncServiceProcessor localSyncServiceProcessor = mock(EpochsLocalSyncServiceProcessor.class);
	private Ledger ledger = mock(Ledger.class);

	public Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(SyncRequestSender.class).toInstance(mock(SyncRequestSender.class));
				bind(EpochsRemoteSyncResponseProcessor.class).toInstance(remoteSyncResponseProcessor);
				bind(EpochsLocalSyncServiceProcessor.class).toInstance(localSyncServiceProcessor);
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(Ledger.class).toInstance(ledger);
				bind(VerifiedValidatorSetSender.class).toInstance(mock(VerifiedValidatorSetSender.class));
				bind(InvalidValidatorSetSender.class).toInstance(mock(InvalidValidatorSetSender.class));
				bind(StateSyncNetwork.class).toInstance(mock(StateSyncNetwork.class));
				bind(SyncTimeoutScheduler.class).toInstance(mock(SyncTimeoutScheduler.class));
				bind(Key.get(new TypeLiteral<Comparator<AccumulatorState>>() { })).toInstance(mock(Comparator.class));
			}
		};
	}

	@Test
	public void given_configured_with_correct_interfaces__when_send_synced_epoch__then_should_commit_to_ledger() {
		Injector injector = Guice.createInjector(new EpochsSyncModule(), getExternalModule());
		injector.injectMembers(this);

		this.syncedEpochSender.sendSyncedEpoch(mock(VerifiedLedgerHeaderAndProof.class));

		verify(ledger, times(1)).commit(any());
	}

	@Test
	public void given_configured_with_correct_interfaces__when_ledger_update__then_should_update_both_processors() {
		Injector injector = Guice.createInjector(new EpochsSyncModule(), getExternalModule());
		injector.injectMembers(this);

		EpochsLedgerUpdate ledgerUpdate = mock(EpochsLedgerUpdate.class);
		this.updateProcessor.processLedgerUpdate(ledgerUpdate);

		verify(remoteSyncResponseProcessor, times(1)).processLedgerUpdate(ledgerUpdate);
		verify(localSyncServiceProcessor, times(1)).processLedgerUpdate(ledgerUpdate);
	}

}
