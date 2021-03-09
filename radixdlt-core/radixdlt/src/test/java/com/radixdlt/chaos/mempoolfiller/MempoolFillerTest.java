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

package com.radixdlt.chaos.mempoolfiller;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.mempool.MempoolThrottleMs;
import com.radixdlt.network.addressbook.PeersView;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import static org.assertj.core.api.Assertions.assertThat;

public class MempoolFillerTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Inject
	@Self
	private BFTNode self;
	@Inject private Hasher hasher;
	@Inject private DeterministicProcessor processor;
	@Inject private DeterministicNetwork network;
	@Inject private RadixEngineStateComputer stateComputer;
	@Inject private SystemCounters systemCounters;
	@Inject private PeersView peersView;

	private Injector getInjector() {
		return Guice.createInjector(
			new SingleNodeAndPeersDeterministicNetworkModule(),
			new MockedGenesisAtomModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
				    install(new MempoolFillerModule());

					bind(ECKeyPair.class).annotatedWith(MempoolFillerKey.class).toInstance(ECKeyPair.generateNew());
					bindConstant().annotatedWith(Names.named("numPeers")).to(0);
					bindConstant().annotatedWith(MempoolMaxSize.class).to(10);
					bindConstant().annotatedWith(MempoolThrottleMs.class).to(10L);
					bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100L));
					bindConstant().annotatedWith(DatabaseLocation.class)
						.to(folder.getRoot().getAbsolutePath());
				}

				@ProvidesIntoSet
				private TokenIssuance mempoolFillerIssuance(@MempoolFillerKey ECPublicKey mempoolFillerKey) {
					return TokenIssuance.of(mempoolFillerKey, TokenUnitConversions.unitsToSubunits(10000000000L));
				}
			}

		);
	}

	@Test
	public void mempool_fill_starts_filling_mempool() {
		// Arrange
		getInjector().injectMembers(this);

		// Act
		processor.handleMessage(self, MempoolFillerUpdate.enable(15));
		processor.handleMessage(self, ScheduledMempoolFill.create());

		// Assert
		assertThat(network.allMessages())
			.areAtLeast(1, new Condition<>(m -> m.message() instanceof MempoolAdd, "Has mempool add"));
	}
}
