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

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.network.p2p.PeersView;
import com.radixdlt.qualifier.NumPeers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Module which injects a full one node network
 */
public final class SingleNodeAndPeersDeterministicNetworkModule extends AbstractModule {
    private final ECKeyPair self;

    public SingleNodeAndPeersDeterministicNetworkModule(ECKeyPair self) {
        this.self = self;
    }

    @Override
    protected void configure() {
        bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(self);
        install(new PersistedNodeForTestingModule());
    }

    @Provides
    @Singleton
    public PeersView peers(@NumPeers int numPeers) {
        final var peers = Stream.generate(BFTNode::random)
            .limit(numPeers)
            .map(PeersView.PeerInfo::fromBftNode)
            .collect(ImmutableList.toImmutableList());
        return peers::stream;
    }

    @Provides
    public List<BFTNode> nodes(@Self BFTNode self) {
        return List.of(self);
    }

    @Provides
    @Singleton
    public DeterministicNetwork network(@Self BFTNode self, PeersView peersView) {
        return new DeterministicNetwork(
            Stream.concat(
                Stream.of(self),
                peersView.peers().map(PeersView.PeerInfo::bftNode)
            ).collect(Collectors.toList()),
            MessageSelector.firstSelector(),
            MessageMutator.nothing()
        );
    }

    @Provides
	@Singleton
    Environment environment(@Self BFTNode self, DeterministicNetwork network) {
        return network.createSender(self);
    }
}
