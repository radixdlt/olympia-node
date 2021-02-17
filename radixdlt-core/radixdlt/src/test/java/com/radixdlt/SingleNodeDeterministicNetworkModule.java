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
 */

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;

import java.util.List;
import java.util.Objects;

/**
 * Module which injects a full one node network
 */
public final class SingleNodeDeterministicNetworkModule extends AbstractModule {
    private final ECKeyPair ecKeyPair;

    public SingleNodeDeterministicNetworkModule(ECKeyPair ecKeyPair) {
        this.ecKeyPair = Objects.requireNonNull(ecKeyPair);
    }

    @Override
    protected void configure() {
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
