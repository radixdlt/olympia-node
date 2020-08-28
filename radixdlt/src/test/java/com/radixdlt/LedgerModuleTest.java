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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.ledger.StateComputerLedger;
import com.radixdlt.ledger.StateComputerLedger.CommittedSender;
import com.radixdlt.ledger.StateComputerLedger.CommittedStateSyncSender;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import org.junit.Test;

public class LedgerModuleTest {
	private static class ExternalLedgerModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(Mempool.class).toInstance(mock(Mempool.class));
			bind(StateComputer.class).toInstance(mock(StateComputer.class));
			bind(CommittedStateSyncSender.class).toInstance(mock(CommittedStateSyncSender.class));
			bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
			VertexMetadata vertexMetadata = mock(VertexMetadata.class);
			bind(VertexMetadata.class).toInstance(vertexMetadata);
			bind(BFTValidatorSet.class).toInstance(mock(BFTValidatorSet.class));
			Multibinder.newSetBinder(binder(), CommittedSender.class);
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
		EpochChange epochChange = injector.getInstance(EpochChange.class);
		assertThat(epochChange).isNotNull();
	}
}