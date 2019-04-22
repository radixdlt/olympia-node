package com.radixdlt.client.core;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomPuller;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.InMemoryAtomStore;
import com.radixdlt.client.core.ledger.InMemoryAtomStoreReducer;
import com.radixdlt.client.core.ledger.RadixAtomPuller;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNetworkController.RadixNetworkControllerBuilder;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.epics.ConnectWebSocketEpic;
import com.radixdlt.client.core.network.epics.DiscoverNodesEpic;
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

import io.reactivex.Observable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A RadixUniverse represents the interface through which a client can interact
 * with a Radix Universe.
 * <p>
 * The configuration file of a Radix Universe defines the genesis atoms of the
 * distributed getLedger and distinguishes this universe from other universes.
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
	public interface Ledger {
		AtomPuller getAtomPuller();

		AtomStore getAtomStore();
	}

	/**
	 * Creates a universe with peer discovery through seed nodes
	 *
	 * @param seeds The seed nodes
	 * @return the created universe
	 */
	public static RadixUniverse create(
		RadixUniverseConfig config,
		Observable<RadixNode> seeds,
		Set<RadixNode> initialNetwork
	) {
		return create(config, Collections.singletonList(new DiscoverNodesEpic(seeds, config)), initialNetwork);
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

	public static RadixUniverse create(BootstrapConfig bootstrapConfig) {
		return create(
			bootstrapConfig.getConfig(),
			bootstrapConfig.getDiscoveryEpics(),
			bootstrapConfig.getInitialNetwork()
		);
	}

	/**
	 * Network Interface
	 */
	private final RadixNetworkController networkController;

	/**
	 * Universe Configuration
	 */
	private final RadixUniverseConfig config;

	private final Ledger ledger;

	private final RRI nativeToken;

	private RadixUniverse(RadixUniverseConfig config, RadixNetworkController networkController, AtomStore atomStore) {
		this.config = config;
		this.networkController = networkController;
		this.nativeToken = config.getGenesis().stream()
			.flatMap(atom -> atom.particles(Spin.UP))
			.filter(p -> p instanceof TokenDefinitionParticle)
			.map(p -> ((TokenDefinitionParticle) p).getRRI())
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No Native Token defined in universe"));

		// Hooking up the default configuration
		// TODO: cleanup
		this.ledger = new Ledger() {
			private final AtomPuller atomPuller = new RadixAtomPuller(networkController);

			@Override
			public AtomPuller getAtomPuller() {
				return atomPuller;
			}

			@Override
			public AtomStore getAtomStore() {
				return atomStore;
			}
		};
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

	public Ledger getLedger() {
		return ledger;
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
		return new RadixAddress(config, publicKey);
	}

	public RadixUniverseConfig getConfig() {
		return config;
	}
}
