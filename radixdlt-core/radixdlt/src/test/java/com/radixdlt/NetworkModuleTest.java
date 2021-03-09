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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BFTEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.network.NetworkModule;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.core.Flowable;
import org.junit.Test;

public class NetworkModuleTest {
	private static class ExternalLedgerModule extends AbstractModule {
		@Override
		protected void configure() {
			final MessageCentral messageCentral = mock(MessageCentral.class);
			when(messageCentral.messagesOf(any())).thenReturn(Flowable.empty());

			bind(BFTNode.class).annotatedWith(Self.class).toInstance(mock(BFTNode.class));
			bind(AddressBook.class).toInstance(mock(AddressBook.class));
			bind(MessageCentral.class).toInstance(messageCentral);
			bind(Hasher.class).toInstance(mock(Hasher.class));
			bind(SystemCounters.class).toInstance(mock(SystemCounters.class));
		}

		@Provides
		@Named("magic")
		public int magic() {
			return 0;
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
			SyncVerticesRPCRx.class,
			ProposalBroadcaster.class,
			BFTEventsRx.class
		);

		classesToCheckFor.forEach(c -> assertThat(injector.getInstance(c)).isNotNull());
	}
}
