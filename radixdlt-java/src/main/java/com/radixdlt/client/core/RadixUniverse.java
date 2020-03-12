package com.radixdlt.client.core;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomPuller;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.InMemoryAtomStore;
import com.radixdlt.client.core.ledger.InMemoryAtomStoreReducer;
import com.radixdlt.client.core.ledger.RadixAtomPuller;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNetworkController.RadixNetworkControllerBuilder;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.epics.ConnectWebSocketEpic;
import com.radixdlt.client.core.network.epics.FetchAtomsEpic;
import com.radixdlt.client.core.network.epics.FindANodeEpic;
import com.radixdlt.client.core.network.epics.RadixJsonRpcAutoCloseEpic;
import com.radixdlt.client.core.network.epics.RadixJsonRpcAutoConnectEpic;
import com.radixdlt.client.core.network.epics.RadixJsonRpcMethodEpic;
import com.radixdlt.client.core.network.epics.SubmitAtomEpic;
import com.radixdlt.client.core.network.epics.WebSocketEventsEpic;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSocketsEpicBuilder;
import com.radixdlt.client.core.network.reducers.RadixNetwork;
import com.radixdlt.client.core.network.selector.RandomSelector;

import java.util.List;
import java.util.Set;

/**
 * A RadixUniverse represents the interface through which a client can interact
 * with a Radix Universe.
 * <p>
 * The configuration file of a Radix Universe defines the genesis atoms of the
 * distributed ledger and distinguishes this universe from other universes.
 * (e.g. Public net vs Test net) It is shared amongst all participants of this
 * instance of the Radix getNetwork.
 * <p>
 * The network interface is available to directly connect with Node Runners in
 * the Radix Network in order to read/write atoms on the distributed ledger.
 * <p>
 * The ledger interface is a thin wrapper on the network interface which provides
 * an abstraction which doesn't require managing network peers. It can for example
 * be used to cache atoms locally.
 */
public final class RadixUniverse {


	public static RadixUniverse create(BootstrapConfig bootstrapConfig) {
		return create(
			bootstrapConfig.getConfig(),
			bootstrapConfig.getDiscoveryEpics(),
			bootstrapConfig.getInitialNetwork()
		);
	}

	/**
	 * Creates a universe with peer discovery through epics
	 *
	 * @param discoveryEpics epics which are responsible for peer discovery
	 * @return the created universe
	 */
	public static RadixUniverse create(
		RadixUniverseConfig config,
		List<RadixNetworkEpic> discoveryEpics,
		Set<RadixNode> initialNetwork
	) {
		return create(config, discoveryEpics, initialNetwork, new WebSockets());
	}

	/**
	 * Creates a universe with peer discovery through epics
	 *
	 * @param discoveryEpics epics which are responsible for peer discovery
	 * @return the created universe
	 */
	public static RadixUniverse create(
		RadixUniverseConfig config,
		List<RadixNetworkEpic> discoveryEpics,
		Set<RadixNode> initialNetwork,
		WebSockets webSockets
	) {
		final InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		config.getGenesis().forEach(atom ->
			atom.addresses()
				.forEach(addr -> inMemoryAtomStore.store(addr, AtomObservation.stored(atom)))
		);

		final InMemoryAtomStoreReducer atomStoreReducer = new InMemoryAtomStoreReducer(inMemoryAtomStore);

		RadixNetworkControllerBuilder builder = new RadixNetworkControllerBuilder()
			.setNetwork(new RadixNetwork())
			.addInitialNodes(initialNetwork)
			.addReducer(atomStoreReducer::reduce)
			.addEpic(
				new WebSocketsEpicBuilder()
					.setWebSockets(webSockets)
					.add(WebSocketEventsEpic::new)
					.add(ConnectWebSocketEpic::new)
					.add(SubmitAtomEpic::new)
					.add(FetchAtomsEpic::new)
					.add(RadixJsonRpcMethodEpic::createGetLivePeersEpic)
					.add(RadixJsonRpcMethodEpic::createGetNodeDataEpic)
					.add(RadixJsonRpcMethodEpic::createGetUniverseEpic)
					.add(RadixJsonRpcAutoConnectEpic::new)
					.add(RadixJsonRpcAutoCloseEpic::new)
					.build()
			)
			.addEpic(new FindANodeEpic(new RandomSelector()));

		discoveryEpics.forEach(builder::addEpic);

		return new RadixUniverse(config, builder.build(), inMemoryAtomStore);
	}

	/**
	 * Network Interface
	 */
	private final RadixNetworkController networkController;

	/**
	 * Universe Configuration
	 */
	private final RadixUniverseConfig config;

	private final AtomPuller puller;

	private final AtomStore atomStore;

	private final RRI nativeToken;

	private RadixUniverse(RadixUniverseConfig config, RadixNetworkController networkController, AtomStore atomStore) {
		this.config = config;
		this.networkController = networkController;
		this.nativeToken = config.getGenesis().stream()
			.flatMap(atom -> atom.particles(Spin.UP))
			.filter(p -> p instanceof FixedSupplyTokenDefinitionParticle)
			.map(p -> ((FixedSupplyTokenDefinitionParticle) p).getRRI())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No Native Token defined in universe"));
		this.atomStore = atomStore;
		this.puller = new RadixAtomPuller(networkController);
	}

	public RadixNetworkController getNetworkController() {
		return networkController;
	}

	public RRI getNativeToken() {
		return nativeToken;
	}

	public int getMagic() {
		return config.getMagic();
	}

	public AtomPuller getAtomPuller() {
		return puller;
	}

	public AtomStore getAtomStore() {
		return atomStore;
	}

	/**
	 * Returns the system public key, also defined as the creator of this Universe
	 *
	 * @return the system public key
	 */
	public ECPublicKey getSystemPublicKey() {
		return config.getSystemPublicKey();
	}

	/**
	 * Maps a public key to it's corresponding Radix address in this universe.
	 * Within a universe, a public key has a one to one bijective relationship to an address
	 *
	 * @param publicKey the key to get an address from
	 * @return the corresponding address to the key for this universe
	 */
	public RadixAddress getAddressFrom(ECPublicKey publicKey) {
		return new RadixAddress(config.getMagicByte(), publicKey);
	}

	public RadixUniverseConfig getConfig() {
		return config;
	}
}
