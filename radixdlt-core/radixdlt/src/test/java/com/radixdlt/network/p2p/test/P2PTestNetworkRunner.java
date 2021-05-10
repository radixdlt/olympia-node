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

package com.radixdlt.network.p2p.test;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.util.Modules;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.DispatcherModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.deterministic.ControlledSenderFactory;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.environment.deterministic.DeterministicMessageProcessor;
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
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.DatabaseCacheSize;
import com.radixdlt.store.DatabaseLocation;
import org.apache.commons.cli.ParseException;
import org.json.JSONObject;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class P2PTestNetworkRunner {

	private final ImmutableList<TestNode> nodes;
	private final DeterministicNetwork deterministicNetwork;

	private P2PTestNetworkRunner(
		ImmutableList<TestNode> nodes,
		DeterministicNetwork deterministicNetwork
	) {
		this.nodes = Objects.requireNonNull(nodes);
		this.deterministicNetwork = Objects.requireNonNull(deterministicNetwork);
	}

	public static P2PTestNetworkRunner create(int numNodes, P2PConfig p2pConfig) throws Exception {
		final var nodesKeys = IntStream.range(0, numNodes)
			.mapToObj(unused -> ECKeyPair.generateNew())
			.collect(ImmutableList.toImmutableList());

		final var network = new DeterministicNetwork(
			nodesKeys.stream().map(key -> BFTNode.create(key.getPublicKey())).collect(Collectors.toList()),
			MessageSelector.firstSelector(),
			MessageMutator.nothing()
		);

		final var p2pNetwork = new MockP2PNetwork();

		final var builder = ImmutableList.<TestNode>builder();
		for (int i = 0; i < numNodes; i++) {
			final var nodeKey = nodesKeys.get(i);
			final var uri = RadixNodeUri.fromPubKeyAndAddress(
				nodeKey.getPublicKey(), "127.0.0.1", p2pConfig.listenPort() + i);
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
		int selfNodeIndex
	) throws ParseException {
		final var properties = new RuntimeProperties(new JSONObject(), new String[] {});
		return Guice.createInjector(
				Modules.override(new P2PModule(properties)).with(
					new AbstractModule() {
						@Override
						protected void configure() {
							bind(PeerOutboundBootstrap.class)
								.toInstance(uri -> p2pNetwork.createChannel(selfNodeIndex, uri));
							bind(P2PConfig.class).toInstance(p2pConfig);
							bind(RadixNodeUri.class).annotatedWith(Self.class).toInstance(selfUri);
						}
					}
				),
				new PeerDiscoveryModule(),
				new PeerLivenessMonitorModule(),
				new DeterministicEnvironmentModule(),
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
						bindConstant().annotatedWith(DatabaseLocation.class)
							.to(dbDir.getRoot().getAbsolutePath());
						bindConstant().annotatedWith(DatabaseCacheSize.class)
							.to((long) (Runtime.getRuntime().maxMemory() * 0.125));
						bind(ECKeyPair.class).annotatedWith(Self.class).toInstance(nodeKey);
						bind(ECPublicKey.class).annotatedWith(Self.class).toInstance(nodeKey.getPublicKey());
						bind(BFTNode.class).annotatedWith(Self.class).toInstance(BFTNode.create(nodeKey.getPublicKey()));
						bind(ECKeyOps.class).toInstance(ECKeyOps.fromKeyPair(nodeKey));
						bind(ControlledSenderFactory.class).toInstance(network::createSender);
						bind(RuntimeProperties.class).toInstance(properties);
						bind(Serialization.class).toInstance(DefaultSerialization.getInstance());
						bind(DeterministicMessageProcessor.class).to(DeterministicProcessor.class);
					}
				}
		);
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
