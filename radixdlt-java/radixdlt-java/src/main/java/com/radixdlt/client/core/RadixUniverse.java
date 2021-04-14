/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core;

import com.radixdlt.atom.SubstateSerializer;
import com.radixdlt.client.core.atoms.Addresses;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.UInt256;
import com.google.common.collect.ImmutableList;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.client.core.ledger.AtomObservation;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.client.fees.FeeEntry;
import com.radixdlt.client.fees.FeeTable;
import com.radixdlt.client.fees.PerBytesFeeEntry;
import com.radixdlt.client.fees.PerParticleFeeEntry;
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
	 * @param config universe config
	 * @param discoveryEpics epics which are responsible for peer discovery
	 * @param initialNetwork nodes in initial network
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
	 * @param config universe config
	 * @param discoveryEpics epics which are responsible for peer discovery
	 * @param initialNetwork nodes in initial network
	 * @param webSockets web sockets
	 * @return the created universe
	 */
	public static RadixUniverse create(
		RadixUniverseConfig config,
		List<RadixNetworkEpic> discoveryEpics,
		Set<RadixNode> initialNetwork,
		WebSockets webSockets
	) {
		final InMemoryAtomStore inMemoryAtomStore = new InMemoryAtomStore();
		var atoms = config.getGenesis();
		for (var atom : atoms) {
			Addresses.ofAtom(atom)
				.forEach(addr -> inMemoryAtomStore.store(addr, AtomObservation.stored(atom, config.timestamp())));
		}

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
		this.nativeToken = config.getGenesis().stream().flatMap(a -> ConstraintMachine.toInstructions(a.getInstructions()).stream())
			.filter(i -> i.getMicroOp() == REInstruction.REOp.UP)
			.map(i -> {
				try {
					return SubstateSerializer.deserialize(i.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.filter(p -> p instanceof TokenDefinitionParticle)
			.map(p -> ((TokenDefinitionParticle) p).getRRI())
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

	/**
	 * Retrieves the fee table for this universe.
	 * @return The fee table for the universe.
	 */
	public FeeTable feeTable() {
		// WARNING: There is a duplicate fee table in TokenFeeModule in core.  If you update this
		// fee table, you will need to change the one there also.
		ImmutableList<FeeEntry> feeEntries = ImmutableList.of(
			// 1 millirad per byte after the first three kilobytes
			PerBytesFeeEntry.of(1,  3072, milliRads(1L)),
			// 1,000 millirads per token definition
			PerParticleFeeEntry.of(TokenDefinitionParticle.class, 0, milliRads(1000L))
		);

		// Minimum fee of 40 millirads
		return FeeTable.from(milliRads(40L), feeEntries);
	}

	private static UInt256 milliRads(long count) {
		// 1 count is 10^{-3} rads, so we subtract that from the sub-units power
		// No risk of overflow here, as 10^18 is approx 60 bits, plus 64 bits of count will not exceed 256 bits
		return UInt256.TEN.pow(TokenUnitConversions.getTokenScale() - 3).multiply(UInt256.from(count));
	}
}
