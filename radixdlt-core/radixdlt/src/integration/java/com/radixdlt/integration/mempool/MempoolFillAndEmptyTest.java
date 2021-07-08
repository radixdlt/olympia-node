/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.mempool;

import com.google.inject.Provides;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.actions.MintToken;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.RERulesConfig;
import com.radixdlt.utils.PrivateKeys;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.api.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.qualifier.NumPeers;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisModule;
import com.radixdlt.statecomputer.forks.RadixEngineForksLatestOnlyModule;
import com.radixdlt.store.DatabaseLocation;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Test which fills a mempool and then empties it checking to make sure there are no
 * stragglers left behind.
 */
public final class MempoolFillAndEmptyTest {
	private static final ECKeyPair TEST_KEY = PrivateKeys.ofNumeric(1);
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

    @Inject private DeterministicProcessor processor;
    @Inject private DeterministicNetwork network;
    @Inject private EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
    @Inject private EventDispatcher<ScheduledMempoolFill> scheduledMempoolFillEventDispatcher;
    @Inject private SystemCounters systemCounters;

	private Injector createInjector() {
		return Guice.createInjector(
			MempoolConfig.asModule(1000, 10),
			new RadixEngineForksLatestOnlyModule(RERulesConfig.testingDefault()),
			new ForksModule(),
			new SingleNodeAndPeersDeterministicNetworkModule(TEST_KEY),
			new MockedGenesisModule(
				Set.of(TEST_KEY.getPublicKey()),
				Amount.ofTokens(1000)
			),
			new MempoolFillerModule(),
			new AbstractModule() {
				@Override
				protected void configure() {
					bindConstant().annotatedWith(NumPeers.class).to(0);
					bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
				}

				@Provides
				@Genesis
				private List<TxAction> mempoolFillerIssuance(@Self ECPublicKey self) {
					return List.of(new MintToken(
						REAddr.ofNativeToken(),
						REAddr.ofPubKeyAccount(self),
						Amount.ofTokens(10000000000L).toSubunits()
					));
				}
			}
		);
	}

    private void fillAndEmptyMempool() {
        while (systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT) < 1000) {
            ControlledMessage msg = network.nextMessage().value();
            processor.handleMessage(msg.origin(), msg.message(), msg.typeLiteral());
            if (msg.message() instanceof EpochViewUpdate) {
                scheduledMempoolFillEventDispatcher.dispatch(ScheduledMempoolFill.create());
            }
        }

        for (int i = 0; i < 10000; i++) {
            ControlledMessage msg = network.nextMessage().value();
            processor.handleMessage(msg.origin(), msg.message(), msg.typeLiteral());
            if (systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT) == 0) {
                break;
            }
        }

        assertThat(systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT)).isZero();
    }

    @Test
    public void check_that_full_mempool_empties_itself() {
        createInjector().injectMembers(this);
        processor.start();

        mempoolFillerUpdateEventDispatcher.dispatch(MempoolFillerUpdate.enable(50, true));

        for (int i = 0; i < 10; i++) {
            fillAndEmptyMempool();
        }

        assertThat(systemCounters.get(SystemCounters.CounterType.RADIX_ENGINE_INVALID_PROPOSED_COMMANDS)).isZero();
    }
}
