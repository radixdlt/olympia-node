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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.epoch.ProposerElectionFactory;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.LedgerUpdateSender;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncRequest;
import org.junit.Test;

public class LedgerModuleTest {
	private static class ExternalLedgerModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(BFTFactory.class).toInstance(mock(BFTFactory.class));
			bind(VertexStoreFactory.class).toInstance(mock(VertexStoreFactory.class));
			bind(PacemakerFactory.class).toInstance(mock(PacemakerFactory.class));

			bind(new TypeLiteral<EventDispatcher<Timeout>>() { }).toInstance(rmock(EventDispatcher.class));
			bind(new TypeLiteral<EventDispatcher<EpochView>>() { }).toInstance(rmock(EventDispatcher.class));

			bind(ProposerElectionFactory.class).toInstance(mock(ProposerElectionFactory.class));
			bind(BFTNode.class).annotatedWith(Self.class).toInstance(mock(BFTNode.class));
			bind(SyncEpochsRPCSender.class).toInstance(mock(SyncEpochsRPCSender.class));
			bind(LocalTimeoutSender.class).toInstance(mock(LocalTimeoutSender.class));
			bind(new TypeLiteral<EventDispatcher<LocalSyncRequest>>() { }).toInstance(rmock(EventDispatcher.class));
			bind(Hasher.class).toInstance(mock(Hasher.class));

			bind(Mempool.class).toInstance(mock(Mempool.class));
			bind(StateComputer.class).toInstance(mock(StateComputer.class));
			bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
			VerifiedLedgerHeaderAndProof verifiedLedgerHeaderAndProof = mock(VerifiedLedgerHeaderAndProof.class);
			bind(VerifiedLedgerHeaderAndProof.class).annotatedWith(LastProof.class).toInstance(verifiedLedgerHeaderAndProof);
			bind(BFTValidatorSet.class).toInstance(mock(BFTValidatorSet.class));
			bind(BFTConfiguration.class).toInstance(mock(BFTConfiguration.class));
			bind(LedgerUpdateSender.class).toInstance(mock(LedgerUpdateSender.class));
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_state_computer_should_be_created() {
		Injector injector = Guice.createInjector(
			new LedgerModule(),
			new ExternalLedgerModule()
		);

		StateComputerLedger stateComputerLedger = injector.getInstance(StateComputerLedger.class);
		assertThat(stateComputerLedger).isNotNull();
	}
}