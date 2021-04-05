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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.radixdlt.SingleNodeAndPeersDeterministicNetworkModule;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.checkpoint.MockedGenesisAtomModule;
import com.radixdlt.store.DatabaseLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Test which fills a mempool and then empties it checking to make sure there are no
 * stragglers left behind.
 */
public final class MempoolFillAndEmptyTest {
    private static final Logger logger = LogManager.getLogger();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Inject private DeterministicEpochsConsensusProcessor processor;
    @Inject private DeterministicNetwork network;
    @Inject private EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
    @Inject private EventDispatcher<ScheduledMempoolFill> scheduledMempoolFillEventDispatcher;
    @Inject private SystemCounters systemCounters;

    private Injector createInjector() {
        return Guice.createInjector(
            new SingleNodeAndPeersDeterministicNetworkModule(),
            new MockedGenesisAtomModule(),
            new MempoolFillerModule(),
            new NodeApplicationModule(),
            new AbstractModule() {
                @Override
                protected void configure() {
                    bindConstant().annotatedWith(Names.named("numPeers")).to(0);
                    bind(MempoolConfig.class).toInstance(MempoolConfig.of(1000L, 10L));
                    bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
                    bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
                }

                @ProvidesIntoSet
                private TokenIssuance mempoolFillerIssuance(@Self RadixAddress self) {
                    return TokenIssuance.of(self.getPublicKey(), TokenUnitConversions.unitsToSubunits(10000000000L));
                }
            }
        );
    }

    private void fillAndEmptyMempool() {
        while (systemCounters.get(SystemCounters.CounterType.MEMPOOL_COUNT) < 1000) {
            ControlledMessage msg = network.nextMessage().value();
            processor.handleMessage(msg.origin(), msg.message());
            if (msg.message() instanceof EpochViewUpdate) {
                scheduledMempoolFillEventDispatcher.dispatch(ScheduledMempoolFill.create());
            }
        }

        for (int i = 0; i < 100000; i++) {
            ControlledMessage msg = network.nextMessage().value();
            processor.handleMessage(msg.origin(), msg.message());
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
