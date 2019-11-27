package org.radix;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.common.Atom;
import org.apache.commons.cli.CommandLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atomos.RRI;
import org.radix.validation.ConstraintMachineValidationException;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.utils.Offset;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.keys.Keys;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import org.radix.utils.IOUtils;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.Bytes;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GenerateUniverses
{
	private static final Logger LOGGER = Logging.getLogger("GenerateUniverses");

	public static final String RADIX_ICON_URL = "https://assets.radixdlt.com/icons/icon-xrd-32x32.png";

	private final Serialization serialization;
	private final boolean standalone;
	private final ECKeyPair universeKey;
	private final ECKeyPair nodeKey;

	public GenerateUniverses(String[] arguments, boolean standalone) throws Exception {
		this.standalone = standalone;
		this.serialization = Serialization.getDefault();

		if (standalone) {
			Security.insertProviderAt(new BouncyCastleProvider(), 1);

			Modules.put(SecureRandom.class, new SecureRandom());
			Modules.put(Serialization.class, this.serialization);

			try {
				JSONObject runtimeConfigurationJSON = new JSONObject();
				if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
					runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

				RuntimeProperties	runtimeProperties = new RuntimeProperties(runtimeConfigurationJSON, arguments);
				Modules.put(RuntimeProperties.class, runtimeProperties);
				Modules.put(PersistedProperties.class, runtimeProperties);
				Modules.put(CommandLine.class, runtimeProperties.getCommandLine());
			} catch (Exception ex) {
				throw new IOException("Could not load runtime properties and set command ling arguments", ex);
			}
		}

		String universeKeyPath = Modules.get(RuntimeProperties.class).get("universe.key.path", "universe.ks");
		universeKey = Keys.readKey(universeKeyPath, "universe", "RADIX_UNIVERSE_KEYSTORE_PASSWORD", "RADIX_UNIVERSE_KEY_PASSWORD");

		// TODO want to be able to specify multiple nodes to get the genesis mass as bootstrapping
		String nodeKeyPath = Modules.get(RuntimeProperties.class).get("node.key.path", "node.ks");
		nodeKey = Keys.readKey(nodeKeyPath, "node", "RADIX_NODE_KEYSTORE_PASSWORD", "RADIX_NODE_KEY_PASSWORD");
	}

	public GenerateUniverses() throws Exception {
		this(new String[] { "universe.key" }, false);
	}

	public List<Universe> generateUniverses() throws Exception {
		if (standalone) {
			LOGGER.info("UNIVERSE KEY PRIVATE:  "+Bytes.toHexString(universeKey.getPrivateKey()));
			LOGGER.info("UNIVERSE KEY PUBLIC:   "+Bytes.toHexString(universeKey.getPublicKey().getBytes()));
			LOGGER.info("NODE KEY PRIVATE:  "+Bytes.toHexString(nodeKey.getPrivateKey()));
			LOGGER.info("NODE KEY PUBLIC:   "+Bytes.toHexString(nodeKey.getPublicKey().getBytes()));
		}

		List<Universe> universes = new ArrayList<>();

		int devPlanckPeriodSeconds = Modules.get(RuntimeProperties.class).get("dev.planck", 60);
		long devPlanckPeriodMillis = TimeUnit.SECONDS.toMillis(devPlanckPeriodSeconds);

		int prodPlanckPeriodSeconds = Modules.get(RuntimeProperties.class).get("prod.planck", 3600);
		long prodPlanckPeriodMillis = TimeUnit.SECONDS.toMillis(prodPlanckPeriodSeconds);

		long universeTimestampSeconds = Modules.get(RuntimeProperties.class).get("universe.timestamp", 1551225600);
		long universeTimestampMillis = TimeUnit.SECONDS.toMillis(universeTimestampSeconds);

		universes.add(buildUniverse(10000, "Radix Mainnet", "The Radix public Universe", UniverseType.PRODUCTION, universeTimestampMillis, prodPlanckPeriodMillis));
		universes.add(buildUniverse(20000, "Radix Testnet", "The Radix test Universe", UniverseType.TEST, universeTimestampMillis, devPlanckPeriodMillis));
		universes.add(buildUniverse(30000, "Radix Devnet", "The Radix development Universe", UniverseType.DEVELOPMENT, universeTimestampMillis, devPlanckPeriodMillis));

		return universes;
	}

	private Universe buildUniverse(int port, String name, String description, UniverseType type, long timestamp, long planckPeriod) throws Exception {
		LOGGER.info("------------------ Starting of Universe: " + type.toString() + " ------------------");
		byte universeMagic = (byte) (Universe.computeMagic(universeKey.getPublicKey(), timestamp, port, type, planckPeriod) & 0xFF);
		List<Atom> universeAtoms = createGenesisAtoms(universeMagic, timestamp, planckPeriod);

		Universe universe = Universe.newBuilder()
			.port(port)
			.name(name)
			.description(description)
			.type(type)
			.timestamp(timestamp)
			.planckPeriod(planckPeriod)
			.creator(universeKey.getPublicKey())
			.addAtoms(universeAtoms)
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

	private List<Atom> createGenesisAtoms(byte magic, long timestamp, long planck) throws Exception {
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

		return Lists.newArrayList(genesisAtom);
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
		GenerateUniverses generateUniverses = new GenerateUniverses(arguments, true);
		generateUniverses.generateUniverses();
	}
}
