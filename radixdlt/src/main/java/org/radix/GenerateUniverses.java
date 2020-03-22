/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.common.Atom;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atomos.RRI;
import org.json.JSONObject;
import org.radix.utils.IOUtils;
import org.radix.validation.ConstraintMachineValidationException;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.utils.Offset;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.keys.Keys;
import com.radixdlt.properties.RuntimeProperties;

import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.Bytes;

import java.io.IOException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class GenerateUniverses
{
	private static final Logger LOGGER = Logging.getLogger("GenerateUniverses");

	public static final String RADIX_ICON_URL = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";

	private final Serialization serialization;
	private final boolean standalone;
	private final RuntimeProperties properties;
	private final ECKeyPair universeKey;
	private final ECKeyPair nodeKey;

	public GenerateUniverses(String[] arguments, boolean standalone, RuntimeProperties properties) throws Exception {
		this.standalone = standalone;
		this.properties = Objects.requireNonNull(properties);
		this.serialization = Serialization.getDefault();

		if (standalone) {
			Security.insertProviderAt(new BouncyCastleProvider(), 1);
		}

		String universeKeyPath = this.properties.get("universe.key.path", "universe.ks");
		universeKey = Keys.readKey(universeKeyPath, "universe", "RADIX_UNIVERSE_KEYSTORE_PASSWORD", "RADIX_UNIVERSE_KEY_PASSWORD");

		// TODO want to be able to specify multiple nodes to get the genesis mass as bootstrapping
		String nodeKeyPath = this.properties.get("node.key.path", "node.ks");
		nodeKey = Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
	}

	public GenerateUniverses(RuntimeProperties properties) throws Exception {
		this(new String[] { "universe.key" }, false, properties);
	}

	public List<Universe> generateUniverses() throws Exception {
		if (standalone) {
			LOGGER.info("UNIVERSE KEY PRIVATE:  "+Bytes.toHexString(universeKey.getPrivateKey()));
			LOGGER.info("UNIVERSE KEY PUBLIC:   "+Bytes.toHexString(universeKey.getPublicKey().getBytes()));
			LOGGER.info("NODE KEY PRIVATE:  "+Bytes.toHexString(nodeKey.getPrivateKey()));
			LOGGER.info("NODE KEY PUBLIC:   "+Bytes.toHexString(nodeKey.getPublicKey().getBytes()));
		}

		List<Universe> universes = new ArrayList<>();

		int devPlanckPeriodSeconds = this.properties.get("dev.planck", 60);
		long devPlanckPeriodMillis = TimeUnit.SECONDS.toMillis(devPlanckPeriodSeconds);

		int prodPlanckPeriodSeconds = this.properties.get("prod.planck", 3600);
		long prodPlanckPeriodMillis = TimeUnit.SECONDS.toMillis(prodPlanckPeriodSeconds);

		long universeTimestampSeconds = this.properties.get("universe.timestamp", 1551225600);
		long universeTimestampMillis = TimeUnit.SECONDS.toMillis(universeTimestampSeconds);

		universes.add(buildUniverse(10000, "Radix Mainnet", "The Radix public Universe", UniverseType.PRODUCTION, universeTimestampMillis, prodPlanckPeriodMillis));
		universes.add(buildUniverse(20000, "Radix Testnet", "The Radix test Universe", UniverseType.TEST, universeTimestampMillis, devPlanckPeriodMillis));
		universes.add(buildUniverse(30000, "Radix Devnet", "The Radix development Universe", UniverseType.DEVELOPMENT, universeTimestampMillis, devPlanckPeriodMillis));

		return universes;
	}

	private Universe buildUniverse(int port, String name, String description, UniverseType type, long timestamp, long planckPeriod) throws Exception {
		LOGGER.info("------------------ Starting of Universe: " + type.toString() + " ------------------");
		byte universeMagic = (byte) (Universe.computeMagic(universeKey.getPublicKey(), timestamp, port, type, planckPeriod) & 0xFF);
		Atom universeAtom = createGenesisAtom(universeMagic, timestamp, planckPeriod);

		Universe universe = Universe.newBuilder()
			.port(port)
			.name(name)
			.description(description)
			.type(type)
			.timestamp(timestamp)
			.planckPeriod(planckPeriod)
			.creator(universeKey.getPublicKey())
			.addAtom(universeAtom)
			.build();
		universe.sign(universeKey);

		if (!universe.verify(universeKey.getPublicKey())) {
			throw new ValidationException("Signature failed for " + name + " universe");
		}
		if (standalone) {
			LOGGER.info(serialization.toJsonObject(universe, Output.API).toString(4));
			byte[] universeBytes = serialization.toDson(universe, Output.WIRE);
			LOGGER.info("UNIVERSE - " + type + ": " + Bytes.toBase64String(universeBytes));
		}
		LOGGER.info("------------------ End of Universe: " + type.toString() + " ------------------");
		return universe;
	}

	private Atom createGenesisAtom(byte magic, long timestamp, long planck) throws Exception {
		RadixAddress universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		UInt256 genesisAmount = UInt256.TEN.pow(TokenDefinitionUtils.SUB_UNITS_POW_10 + 9); // 10^9 = 1,000,000,000 pieces of eight, please
		FixedSupplyTokenDefinitionParticle xrdDefinition = createTokenDefinition(magic, "XRD", "Rads", "Radix Native Tokens", genesisAmount);
		MessageParticle helloUniverseMessage = createHelloMessage(universeAddress);
		RRIParticle rriParticle = new RRIParticle(xrdDefinition.getRRI());
		TransferrableTokensParticle mintXrdTokens = createGenesisXRDMint(universeAddress, "XRD", genesisAmount, timestamp, planck);

		Atom genesisAtom = new Atom(timestamp);
		genesisAtom.addParticleGroupWith(helloUniverseMessage, Spin.UP);
		genesisAtom.addParticleGroupWith(
			rriParticle, Spin.DOWN,
			xrdDefinition, Spin.UP,
			mintXrdTokens, Spin.UP
		);
		genesisAtom.sign(universeKey);

		if (standalone) {
			byte[] sigBytes = serialization.toDson(genesisAtom.getSignature(universeKey.getUID()), Output.WIRE);
			byte[] transactionBytes = serialization.toDson(genesisAtom, Output.HASH);
			LOGGER.info("GENESIS TRANSACTION SIGNATURE " + universeKey.getUID() + ": " + Bytes.toHexString(sigBytes));
			LOGGER.info("GENESIS TRANSACTION HID: " + genesisAtom.getHash().getID());
			LOGGER.info("GENESIS TRANSACTION HASH: " + genesisAtom.getHash().toString());
			LOGGER.info("GENESIS TRANSACTION HASH DSON: " + Base64.getEncoder().encodeToString(transactionBytes));
		}

		if (!genesisAtom.verify(universeKey.getPublicKey())) {
			throw new ConstraintMachineValidationException(
				genesisAtom,
				"Signature generation failed - GENESIS TRANSACTION HASH: " + genesisAtom.getHash().toString(),
				DataPointer.ofAtom()
			);
		}

		return genesisAtom;
	}

	/*
	 * Create the 'hello' message particle at the given universes
	 */
	private static MessageParticle createHelloMessage(RadixAddress address) {
		return new MessageParticle(address, address, "Radix... just imagine!".getBytes(RadixConstants.STANDARD_CHARSET));
	}

	private static TransferrableTokensParticle createGenesisXRDMint(
		RadixAddress address,
		String symbol,
		UInt256 amount,
		long timestamp,
		long planck
	) {
		return new TransferrableTokensParticle(
			address,
			amount,
			UInt256.ONE,
			RRI.of(address, TokenDefinitionUtils.getNativeTokenShortCode()),
			Universe.computePlanck(timestamp, planck, Offset.NONE),
			ImmutableMap.of()
		);
	}

	/*
	 * Create a token definition as a genesis token with the radix icon and granularity of 1
	 */
	private FixedSupplyTokenDefinitionParticle createTokenDefinition(
		byte magic,
		String symbol,
		String name,
		String description,
		UInt256 supply
	) {
		return new FixedSupplyTokenDefinitionParticle(
			RRI.of(new RadixAddress(magic, universeKey.getPublicKey()), symbol),
			name,
			description,
			supply,
			UInt256.ONE,
			RADIX_ICON_URL
		);
	}

	public static void main(String[] arguments) throws Exception {
		RuntimeProperties properties = loadProperties(arguments);

		GenerateUniverses generateUniverses = new GenerateUniverses(arguments, true, properties);
		generateUniverses.generateUniverses();
	}

	private static RuntimeProperties loadProperties(String[] arguments) throws IOException {
		try {
			JSONObject runtimeConfigurationJSON = new JSONObject();
			if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
				runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

			return new RuntimeProperties(runtimeConfigurationJSON, arguments);
		} catch (Exception ex) {
			throw new IOException("while loading runtime properties", ex);
		}
	}


}
