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

package com.radixdlt.api.chaos.mempoolfiller;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.utils.PrivateKeys;
import org.assertj.core.api.Condition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class MempoolFillerTest {
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);

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
			new RadixEngineForksLatestOnlyModule(),
			MempoolConfig.asModule(10, 10),
			new ForksModule(),
			new MainnetForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(10000000000L),
				Amount.ofTokens(100)
			),
			new AbstractModule() {
				@Override
				protected void configure() {
				    install(new MempoolFillerModule());
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}
			}
		);
	}

	@Test
	public void mempool_fill_starts_filling_mempool() {
		// Arrange
		getInjector().injectMembers(this);

		// Act
		processor.handleMessage(self, MempoolFillerUpdate.enable(15, true), null);
		processor.handleMessage(self, ScheduledMempoolFill.create(), null);

		// Assert
		assertThat(network.allMessages())
			.areAtLeast(1, new Condition<>(m -> m.message() instanceof MempoolAdd, "Has mempool add"));
	}
}
