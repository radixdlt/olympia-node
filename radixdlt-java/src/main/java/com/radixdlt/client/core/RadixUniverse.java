package com.radixdlt.client.core;

import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.core.ledger.RadixParticleStore;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.ledger.AtomFetcher;
import com.radixdlt.client.core.ledger.AtomPuller;
import com.radixdlt.client.core.ledger.AtomStore;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.ledger.ParticleStore;
import com.radixdlt.client.core.ledger.RadixAtomPuller;
import com.radixdlt.client.core.ledger.RadixAtomSubmitter;
import com.radixdlt.client.core.ledger.ClientSelector;
import com.radixdlt.client.core.ledger.InMemoryAtomStore;
import com.radixdlt.client.core.network.PeerDiscovery;
import com.radixdlt.client.core.network.RadixNetwork;
import java.util.Optional;

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

		ParticleStore getParticleStore();

		AtomStore getAtomStore();

		AtomSubmitter getAtomSubmitter();
	}

	/**
	 * Lock to protect default Radix Universe instance
	 */
	private static Object lock = new Object();

	/**
	 * Default Universe Instance
	 */
	private static RadixUniverse defaultUniverse;

	// TODO: don't check universe for betanet, enable in future
	private static final boolean CHECK_UNIVERSE = false;


	/**
	 * Initializes the default universe with a Peer Discovery mechanism.
	 * Should only be called once at the start of the program.
	 *
	 * @param peerDiscovery The peer discovery mechanism
	 * @return The default universe created, can also be retrieved with RadixUniverse.getInstance()
	 */
	public static RadixUniverse bootstrap(
		RadixUniverseConfig config,
		PeerDiscovery peerDiscovery
	) {
		synchronized (lock) {
			if (defaultUniverse != null) {
				throw new IllegalStateException("Default Universe already bootstrapped");
			}

			RadixNetwork network = new RadixNetwork(peerDiscovery);

			defaultUniverse = new RadixUniverse(config, network);

			return defaultUniverse;
		}
	}

	public static RadixUniverse bootstrap(BootstrapConfig bootstrapConfig) {
		return bootstrap(bootstrapConfig.getConfig(), bootstrapConfig.getDiscovery());
	}

	/**
	 * Returns the default RadixUniverse instance
	 * @return the default RadixUniverse instance
	 */
	public static RadixUniverse getInstance() {
		synchronized (lock) {
			if (defaultUniverse == null) {
				throw new IllegalStateException("Default Universe was not initialized via RadixUniverse.bootstrap()");
			}
			return defaultUniverse;
		}
	}

	/**
	 * Network Interface
	 */
	private final RadixNetwork network;

	/**
	 * Universe Configuration
	 */
	private final RadixUniverseConfig config;

	private final Ledger ledger;

	private final TokenClassReference powToken;

	private final TokenClassReference nativeToken;

	private RadixUniverse(RadixUniverseConfig config, RadixNetwork network) {
		this.config = config;
		this.network = network;

		final Optional<TokenClassReference> powToken = config.getGenesis().stream()
			.flatMap(atom -> atom.particles(Spin.UP))
			.filter(p -> p instanceof TokenParticle)
			.filter(p -> ((TokenParticle) p).getTokenClassReference().getIso().equals("POW"))
			.map(p -> ((TokenParticle) p).getTokenClassReference())
			.findFirst();

		if (!powToken.isPresent()) {
			throw new IllegalStateException("No POW Token defined in universe");
		}

		this.powToken = powToken.get();

		final Optional<TokenClassReference> nativeToken = config.getGenesis().stream()
			.flatMap(atom -> atom.particles(Spin.UP))
			.filter(p -> p instanceof TokenParticle)
			.filter(p -> !((TokenParticle) p).getTokenClassReference().getIso().equals("POW"))
			.map(p -> ((TokenParticle) p).getTokenClassReference())
			.findFirst();

		if (!nativeToken.isPresent()) {
			throw new IllegalStateException("No Native Token defined in universe");
		}

		this.nativeToken = nativeToken.get();

		final InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		config.getGenesis().forEach(atom ->
			atom.addresses()
				.map(this::getAddressFrom)
				.forEach(addr -> inMemoryAtomStore.store(addr, AtomObservation.storeAtom(atom)))
		);

		// Hooking up the default configuration
		// TODO: cleanup
		this.ledger = new Ledger() {
			private final ClientSelector clientSelector = new ClientSelector(config, network, CHECK_UNIVERSE);
			private final AtomFetcher atomFetcher = new AtomFetcher(clientSelector::getRadixClient);
			private final AtomPuller atomPuller = new RadixAtomPuller(atomFetcher::fetchAtoms, inMemoryAtomStore::store);
			private final AtomSubmitter atomSubmitter = new RadixAtomSubmitter(clientSelector::getRadixClient);

			/**
			* The Particle Data Store
			* TODO: actually change it into the particle data store
			*/
			private final RadixParticleStore particleStore = new RadixParticleStore(inMemoryAtomStore);

			@Override
			public AtomPuller getAtomPuller() {
				return atomPuller;
			}

			@Override
			public RadixParticleStore getParticleStore() {
				return particleStore;
			}

			@Override
			public AtomStore getAtomStore() {
				return inMemoryAtomStore;
			}

			@Override
			public AtomSubmitter getAtomSubmitter() {
				return atomSubmitter;
			}
		};
	}

	public TokenClassReference getPOWToken() {
		return powToken;
	}

	public TokenClassReference getNativeToken() {
		return nativeToken;
	}

	public int getMagic() {
		return config.getMagic();
	}

	public Ledger getLedger() {
		return ledger;
	}

	public RadixNetwork getNetwork() {
		return network;
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

	/**
	 * Attempts to gracefully free all resources associated with this Universe
	 */
	public void disconnect() {
		network.close();
	}

	public RadixUniverseConfig getConfig() {
		return config;
	}
}
