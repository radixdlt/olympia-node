package org.radix;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atomos.RadixAddress;
import com.radixdlt.atommodel.message.MessageParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.atommodel.tokens.Tokens;
import org.radix.atoms.Atom;
import com.radixdlt.atomos.RRI;
import org.radix.validation.ConstraintMachineValidationException;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import com.radixdlt.utils.Offset;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import org.radix.exceptions.ValidationException;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import org.radix.time.TemporalVertex;
import org.radix.time.Timestamps;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import org.radix.utils.IOUtils;
import com.radixdlt.utils.RadixConstants;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.Bytes;
import org.radix.validation.Validation;

import java.io.File;
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
			Security.addProvider(new BouncyCastleProvider());

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
				new Validation().start_impl(); // not sure if this is a good way to about this
			} catch (Exception ex) {
				throw new IOException("Could not load runtime properties and set command ling arguments", ex);
			}
		}

		String universeKeyPath = Modules.get(RuntimeProperties.class).get("universe.key.path", "universe.key");
		universeKey = ECKeyPair.fromFile(new File(universeKeyPath), true);

		// TODO want to be able to specify multiple nodes to get the genesis mass as bootstrapping
		String nodeKeyPath = Modules.get(RuntimeProperties.class).get("node.key.path", "node.key");
		nodeKey = ECKeyPair.fromFile(new File(nodeKeyPath), true);
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
		byte universeMagic = (byte) (Universe.computeMagic(universeKey.getPublicKey(), timestamp, port, type, planckPeriod) & 0xFF);
		List<Atom> universeAtoms = createGenesisAtoms(universeMagic, timestamp, planckPeriod);
		doTemporalProofs(timestamp, universeAtoms);

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
			System.out.println(serialization.toJsonObject(universe, Output.WIRE).toString(4));
			byte[] universeBytes = serialization.toDson(universe, Output.WIRE);
			System.out.println("UNIVERSE - " + type + ": "+Bytes.toBase64String(universeBytes));
		}

		return universe;
	}

	private List<Atom> createGenesisAtoms(byte magic, long timestamp, long planck) throws Exception {
		RadixAddress universeAddress = new RadixAddress(magic, universeKey.getPublicKey());
		ImmutableMap<TokenTransition, TokenPermission> tokenPermissions =
			ImmutableMap.of(
				TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY,
				TokenTransition.BURN, TokenPermission.NONE
			);
		TokenDefinitionParticle xrdDefinition = createTokenDefinition(magic, "XRD", "Rads", "Radix Native Tokens", tokenPermissions);
		MessageParticle helloUniverseMessage = createHelloMessage(universeAddress);
		RRIParticle rriParticle = new RRIParticle(xrdDefinition.getRRI());

		UInt256 genesisAmount = UInt256.TEN.pow(TokenDefinitionParticle.SUB_UNITS_POW_10 + 9); // 10^9 = 1,000,000,000 pieces of eight, please

		UnallocatedTokensParticle unallocatedTokensParticle = createGenesisXRDUnallocated(
			UInt256.MAX_VALUE,
			universeAddress,
			tokenPermissions
		);

		TransferrableTokensParticle mintXrdTokens = createGenesisXRDMint(genesisAmount, timestamp, planck, universeAddress);
		UnallocatedTokensParticle unallocatedLeftoverTokensParticle = createGenesisXRDUnallocated(
			UInt256.MAX_VALUE.subtract(genesisAmount),
			universeAddress,
			tokenPermissions
		);

		Atom genesisAtom = new Atom(timestamp);
		genesisAtom.addParticleGroupWith(helloUniverseMessage, Spin.UP);
		genesisAtom.addParticleGroupWith(
			rriParticle, Spin.DOWN,
			xrdDefinition, Spin.UP,
			unallocatedTokensParticle, Spin.UP
		);
		genesisAtom.addParticleGroupWith(
			unallocatedTokensParticle, Spin.DOWN,
			unallocatedLeftoverTokensParticle, Spin.UP,
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

	private static TransferrableTokensParticle createGenesisXRDMint(UInt256 amount, long timestamp, long planck, RadixAddress address) {
		return new TransferrableTokensParticle(
			address,
			amount,
			UInt256.ONE,
			RRI.of(address, Tokens.getNativeTokenShortCode()),
			Universe.computePlanck(timestamp, planck, Offset.NONE),
			ImmutableMap.of(TokenTransition.MINT, TokenPermission.TOKEN_CREATION_ONLY, TokenTransition.BURN, TokenPermission.NONE)
		);
	}

	private static UnallocatedTokensParticle createGenesisXRDUnallocated(
		UInt256 amount,
		RadixAddress address,
		ImmutableMap<TokenTransition, TokenPermission> tokenPermissions
	) {
		return new UnallocatedTokensParticle(
			amount,
			UInt256.ONE,
			RRI.of(address, Tokens.getNativeTokenShortCode()),
			tokenPermissions
		);
	}

	/*
	 * Create a token definition as a genesis token with the radix icon and granularity of 1
	 */
	private TokenDefinitionParticle createTokenDefinition(
		byte magic,
		String symbol,
		String name,
		String description,
		ImmutableMap<TokenTransition, TokenPermission> tokenPermissions
	) {
		return new TokenDefinitionParticle(
			new RadixAddress(magic, universeKey.getPublicKey()),
			symbol,
			name,
			description,
			UInt256.ONE,
			RADIX_ICON_URL,
			tokenPermissions
		);
	}

	private void doTemporalProofs(long timestamp, List<Atom> genesis) throws ValidationException, CryptoException	{
		String universeBootstrap = Modules.get(RuntimeProperties.class).get("universe.bootstrap", "").trim();
		if (universeBootstrap.isEmpty()) {
			String privKey = Bytes.toHexString(nodeKey.getPrivateKey());
			LOGGER.info("WARNING: Universe bootstrap nodes are not defined or declared, using default " + privKey);
			universeBootstrap = privKey;
		}

		long clock = 0L;
		for (Atom atom : genesis)
		{
			clock += 1;
			TemporalVertex temporalVertex = null;
			for (String temporalProofNodeKey : universeBootstrap.split(","))
			{
				EUID prev = temporalVertex == null ? EUID.ZERO : temporalVertex.getHID();
				byte[] key = Bytes.fromHexString(temporalProofNodeKey);
				ECKeyPair bootstrapKey = new ECKeyPair(key);
				temporalVertex = new TemporalVertex(bootstrapKey.getPublicKey(), clock++, timestamp, Hash.ZERO_HASH, prev);
				// Make vertex timestamp match the rest of the universe
				temporalVertex.setTimestamp(Timestamps.DEFAULT, timestamp);
				atom.getTemporalProof().add(temporalVertex, bootstrapKey);
			}

			if (!atom.getTemporalProof().hasVertexByNID(nodeKey.getUID()))
			{
				EUID prev = temporalVertex == null ? EUID.ZERO : temporalVertex.getHID();
				temporalVertex = new TemporalVertex(nodeKey.getPublicKey(), clock, timestamp, Hash.ZERO_HASH, prev);
				// Make vertex timestamp match the rest of the universe
				temporalVertex.setTimestamp(Timestamps.DEFAULT, timestamp);
				atom.getTemporalProof().add(temporalVertex, nodeKey);
			}
		}
	}

	public static void main(String[] arguments) throws Exception {
		GenerateUniverses generateUniverses = new GenerateUniverses(arguments, true);
		generateUniverses.generateUniverses();
	}
}
