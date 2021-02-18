package com.radixdlt.integration.mempool;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.SingleNodeDeterministicNetworkModule;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerKey;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerModule;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.mempoolfiller.ScheduledMempoolFill;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.deterministic.DeterministicEpochsConsensusProcessor;
import com.radixdlt.environment.deterministic.network.ControlledMessage;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.mempool.MempoolMaxSize;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.store.DatabaseLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.radix.TokenIssuance;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class MempoolFillAndEmptyTest {
    private static final Logger logger = LogManager.getLogger();
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private ECKeyPair ecKeyPair = ECKeyPair.generateNew();

    @Inject
    private DeterministicEpochsConsensusProcessor processor;
    @Inject private DeterministicNetwork network;
    @Inject private EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
    @Inject private EventDispatcher<ScheduledMempoolFill> scheduledMempoolFillEventDispatcher;
    @Inject private SystemCounters systemCounters;

    private Injector createInjector(ECKeyPair ecKeyPair) {
        return Guice.createInjector(
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bindConstant().annotatedWith(MempoolMaxSize.class).to(1000);
                        bindConstant().annotatedWith(DatabaseLocation.class).to(folder.getRoot().getAbsolutePath());
                        bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100));
                    }

                    @ProvidesIntoSet
                    private TokenIssuance mempoolFillerIssuance(@MempoolFillerKey ECPublicKey mempoolFillerKey) {
                        return TokenIssuance.of(mempoolFillerKey, TokenUnitConversions.unitsToSubunits(10000000000L));
                    }
                },
                new SingleNodeDeterministicNetworkModule(ecKeyPair),
                new MempoolFillerModule()
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
        createInjector(ecKeyPair).injectMembers(this);
        processor.start();

        mempoolFillerUpdateEventDispatcher.dispatch(MempoolFillerUpdate.create(true));

        for (int i = 0; i < 10; i++) {
            fillAndEmptyMempool();
        }
    }
}
