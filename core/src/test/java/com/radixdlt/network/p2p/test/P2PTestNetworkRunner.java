/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p.test;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.modules.DispatcherModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.monitoring.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.StartProcessorOnRunner;
import com.radixdlt.environment.deterministic.DeterministicProcessor;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork;
import com.radixdlt.environment.deterministic.network.MessageMutator;
import com.radixdlt.environment.deterministic.network.MessageSelector;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.P2PModule;
import com.radixdlt.network.p2p.PeerDiscoveryModule;
import com.radixdlt.network.p2p.PeerLivenessMonitorModule;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.addressbook.AddressBook;
import com.radixdlt.network.p2p.transport.PeerOutboundBootstrap;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.statecomputer.forks.FixedEpochForkConfig;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.NewestForkConfig;
import com.radixdlt.store.DatabaseCacheSize;
import com.radixdlt.store.DatabaseEnvironment;
import com.radixdlt.store.DatabaseLocation;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.IntStream;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.rules.TemporaryFolder;

public final class P2PTestNetworkRunner {
  private final ImmutableList<TestNode> nodes;
  private final DeterministicNetwork deterministicNetwork;

  public static final class TestCounters {
    public int outboundChannelsBootstrapped;
  }

  private P2PTestNetworkRunner(
      ImmutableList<TestNode> nodes, DeterministicNetwork deterministicNetwork) {
    this.nodes = Objects.requireNonNull(nodes);
    this.deterministicNetwork = Objects.requireNonNull(deterministicNetwork);
  }

  public static P2PTestNetworkRunner create(int numNodes, P2PConfig p2pConfig) throws Exception {
    final var nodesKeys =
        IntStream.range(0, numNodes)
            .mapToObj(unused -> ECKeyPair.generateNew())
            .collect(ImmutableList.toImmutableList());

    final var network =
        new DeterministicNetwork(
            nodesKeys.stream().map(key -> BFTNode.create(key.getPublicKey())).toList(),
            MessageSelector.firstSelector(),
            MessageMutator.nothing());

    final var p2pNetwork = new MockP2PNetwork();

    final var builder = ImmutableList.<TestNode>builder();
    for (int i = 0; i < numNodes; i++) {
      final var nodeKey = nodesKeys.get(i);
      final var uri =
          RadixNodeUri.fromPubKeyAndAddress(
              1, nodeKey.getPublicKey(), "127.0.0.1", p2pConfig.listenPort() + i);
      final var injector = createInjector(p2pNetwork, network, p2pConfig, nodeKey, uri, i);
      builder.add(new TestNode(injector, uri, nodeKey));
    }

    final var injectors = builder.build();

    p2pNetwork.setNodes(injectors);

    return new P2PTestNetworkRunner(injectors, network);
  }

  private static Injector createInjector(
      MockP2PNetwork p2pNetwork,
      DeterministicNetwork network,
      P2PConfig p2pConfig,
      ECKeyPair nodeKey,
      RadixNodeUri selfUri,
      int selfNodeIndex)
      throws ParseException {
    final var properties = new RuntimeProperties(new JSONObject(), new String[] {});
    return Guice.createInjector(
        Modules.override(new P2PModule(properties))
            .with(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(TestCounters.class).toInstance(new TestCounters());
                    bind(P2PConfig.class).toInstance(p2pConfig);
                    bind(RadixNodeUri.class).annotatedWith(Self.class).toInstance(selfUri);
                    bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
                  }

                  @Provides
                  public PeerOutboundBootstrap peerOutboundBootstrap(TestCounters testCounters) {
                    return uri -> {
                      testCounters.outboundChannelsBootstrapped += 1;
                      p2pNetwork.createChannel(selfNodeIndex, uri);
                    };
                  }
                }),
        new PeerDiscoveryModule(),
        new PeerLivenessMonitorModule(),
        new DispatcherModule(),
        new AbstractModule() {
          @Override
          protected void configure() {
            final var dbDir = new TemporaryFolder();
            try {
              dbDir.create();
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
            bindConstant().annotatedWith(NetworkId.class).to(Network.LOCALNET.getId());
            bind(Addressing.class).toInstance(Addressing.ofNetwork(Network.LOCALNET));
            bindConstant()
                .annotatedWith(DatabaseLocation.class)
                .to(dbDir.getRoot().getAbsolutePath());
            bindConstant().annotatedWith(DatabaseCacheSize.class).to(100_000L);
            bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(nodeKey);
            bind(ECPublicKey.class).annotatedWith(Self.class).toInstance(nodeKey.getPublicKey());
            bind(BFTNode.class)
                .annotatedWith(Self.class)
                .toInstance(BFTNode.create(nodeKey.getPublicKey()));
            bind(String.class)
                .annotatedWith(Self.class)
                .toInstance(
                    Addressing.ofNetwork(Network.LOCALNET)
                        .forValidators()
                        .of(nodeKey.getPublicKey())
                        .substring(0, 10));
            bind(ECKeyOps.class).toInstance(ECKeyOps.fromKeyPair(nodeKey));
            bind(Environment.class)
                .toInstance(network.createSender(BFTNode.create(nodeKey.getPublicKey())));
            bind(RuntimeProperties.class).toInstance(properties);
            bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
            bind(DeterministicProcessor.class);
            Multibinder.newSetBinder(binder(), StartProcessorOnRunner.class);
            bind(ForkConfig.class)
                .annotatedWith(NewestForkConfig.class)
                .toInstance(new FixedEpochForkConfig("genesis", null, 0L));
          }
        });
  }

  public void cleanup() {
    this.nodes.forEach(
        node -> {
          node.injector.getInstance(DatabaseEnvironment.class).stop();
        });
  }

  public RadixNodeUri getUri(int nodeIndex) {
    return this.nodes.get(nodeIndex).uri;
  }

  public PeerManager peerManager(int nodeIndex) {
    return getInstance(nodeIndex, PeerManager.class);
  }

  public AddressBook addressBook(int nodeIndex) {
    return getInstance(nodeIndex, AddressBook.class);
  }

  public <T> T getInstance(int nodeIndex, Class<T> clazz) {
    return this.nodes.get(nodeIndex).injector.getInstance(clazz);
  }

  public <T> T getInstance(int nodeIndex, Key<T> key) {
    return this.nodes.get(nodeIndex).injector.getInstance(key);
  }

  public DeterministicNetwork getDeterministicNetwork() {
    return this.deterministicNetwork;
  }

  public TestNode getNode(int index) {
    return this.nodes.get(index);
  }
}
