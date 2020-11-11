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

import static com.radixdlt.utils.TypedMocks.rmock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.epochs.SyncedEpochSender;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.store.LastEpochProof;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncTimeoutScheduler;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.InvalidValidatorSetSender;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier.VerifiedValidatorSetSender;
import com.radixdlt.sync.StateSyncNetworkSender;
import com.radixdlt.sync.SyncPatienceMillis;
import com.radixdlt.utils.TypedMocks;

import java.util.Comparator;
import org.junit.Test;

public class EpochsSyncModuleTest {
	@Inject
	private SyncedEpochSender syncedEpochSender;

	private Ledger ledger = mock(Ledger.class);

	public Module getExternalModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				bind(VerifiedLedgerHeaderAndProof.class).annotatedWith(LastProof.class)
					.toInstance(mock(VerifiedLedgerHeaderAndProof.class));
				bind(VerifiedLedgerHeaderAndProof.class).annotatedWith(LastEpochProof.class)
					.toInstance(mock(VerifiedLedgerHeaderAndProof.class));
				bind(EpochChange.class).toInstance(mock(EpochChange.class));
				bind(LocalSyncServiceAccumulatorProcessor.class).toInstance(mock(LocalSyncServiceAccumulatorProcessor.class));
				bind(RemoteSyncResponseValidatorSetVerifier.class).toInstance(mock(RemoteSyncResponseValidatorSetVerifier.class));
				bind(new TypeLiteral<EventDispatcher<LocalSyncRequest>>() { }).toInstance(rmock(EventDispatcher.class));
				bind(new TypeLiteral<RemoteEventDispatcher<DtoLedgerHeaderAndProof>>() { }).toInstance(rmock(RemoteEventDispatcher.class));
				bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
				bind(Ledger.class).toInstance(ledger);
				bind(VerifiedValidatorSetSender.class).toInstance(mock(VerifiedValidatorSetSender.class));
				bind(InvalidValidatorSetSender.class).toInstance(mock(InvalidValidatorSetSender.class));
				bind(StateSyncNetworkSender.class).toInstance(mock(StateSyncNetworkSender.class));
				bind(SyncTimeoutScheduler.class).toInstance(mock(SyncTimeoutScheduler.class));
				bind(new TypeLiteral<Comparator<AccumulatorState>>() { }).toInstance(TypedMocks.rmock(Comparator.class));
				bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
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
}
