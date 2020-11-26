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
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.universe.Universe;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class NetworkModuleTest {
	private static class ExternalLedgerModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(BFTNode.class).annotatedWith(Self.class).toInstance(mock(BFTNode.class));
			bind(Universe.class).toInstance(mock(Universe.class));
			bind(AddressBook.class).toInstance(mock(AddressBook.class));
			bind(MessageCentral.class).toInstance(mock(MessageCentral.class));
			bind(Hasher.class).toInstance(mock(Hasher.class));
			bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
		}
	}

	@Test
	public void when_configured_with_correct_interfaces__then_state_computer_should_be_created() {
		Injector injector = Guice.createInjector(
			new NetworkModule(),
			new ExternalLedgerModule()
		);

		List<Class<?>> classesToCheckFor = Arrays.asList(
			SyncEpochsRPCSender.class,
			SyncEpochsRPCRx.class,
			SyncVerticesRequestSender.class,
			SyncVerticesRPCRx.class,
			ProposalBroadcaster.class,
			ProceedToViewSender.class,
			BFTEventsRx.class
		);

		classesToCheckFor.forEach(c -> assertThat(injector.getInstance(c)).isNotNull());
	}
}