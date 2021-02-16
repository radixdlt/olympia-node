package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.statecomputer.EpochCeilingView;

import java.util.List;
import java.util.Objects;

public class SingleNodeDeterministicNetworkModule extends AbstractModule {
    private final ECKeyPair ecKeyPair;

    public SingleNodeDeterministicNetworkModule(ECKeyPair ecKeyPair) {
        this.ecKeyPair = Objects.requireNonNull(ecKeyPair);
    }

    @Override
    protected void configure() {
        bind(View.class).annotatedWith(EpochCeilingView.class).toInstance(View.of(100L));
        bind(ECKeyPair.class).annotatedWith(Names.named("universeKey")).toInstance(ecKeyPair);
        install(new PersistedNodeForTestingModule(ecKeyPair));
    }

    @Provides
    public List<BFTNode> nodes(@Self BFTNode self) {
        return List.of(self);
    }

    @Provides
    @Singleton
    public DeterministicNetwork network(@Self BFTNode self) {
        return new DeterministicNetwork(
            List.of(self),
            MessageSelector.firstSelector(),
            MessageMutator.nothing()
        );
    }

    @Provides
    ControlledSenderFactory senderFactory(DeterministicNetwork network) {
        return network::createSender;
    }
}
