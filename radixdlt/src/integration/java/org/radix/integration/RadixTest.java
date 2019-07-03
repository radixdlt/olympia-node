package org.radix.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.radix.GenerateUniverses;
import org.radix.Radix;

import com.radixdlt.atoms.Atom;
import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;

import org.radix.exceptions.ValidationException;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;
import org.radix.time.TemporalProof;
import org.radix.time.TemporalVertex;
import org.radix.time.Time;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;
import org.radix.validation.Validation;

public class RadixTest
{
	private static String dbLocation = null;

	private long clock = 100L; // Arbitrary starting point. Must be larger than number of atoms in genesis.

	@BeforeClass
	public static void startRadixTest() throws Exception
	{
		Field isRestricted = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");

		System.out.print("Encryption restrictions are set, need to override...");

		if ( Modifier.isFinal(isRestricted.getModifiers()) )
		{
			Field modifiers = Field.class.getDeclaredField("modifiers");
			modifiers.setAccessible(true);
			modifiers.setInt(isRestricted, isRestricted.getModifiers() & ~Modifier.FINAL);
		}

		isRestricted.setAccessible(true);
		isRestricted.setBoolean(null, false);
		isRestricted.setAccessible(false);
		System.out.println("...override success!");

		Security.addProvider(new BouncyCastleProvider());

		Serialization serialization = Serialization.create(ClasspathScanningSerializerIds.create(), ClasspathScanningSerializationPolicy.create());

		Modules.put(SecureRandom.class, new SecureRandom());

		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

		RuntimeProperties	runtimeProperties = new RuntimeProperties(runtimeConfigurationJSON, null);
		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(PersistedProperties.class, runtimeProperties);
		Modules.put(CommandLine.class, runtimeProperties.getCommandLine());
		Modules.put(Serialization.class, serialization);

		Modules.get(RuntimeProperties.class).set("debug.nopow", true);
		if (dbLocation == null) {
			// Avoid RADIXDB_TEST_TEST_TEST_TEST_TEST situation
			dbLocation = Modules.get(RuntimeProperties.class).get("db.location", ".//RADIXDB") + "_TEST";
		}
		Modules.get(RuntimeProperties.class).set("db.location", dbLocation);

		Modules.getInstance().start(new Time());
		Modules.getInstance().start(new Validation());

		Universe universe = new GenerateUniverses().generateUniverses().stream().filter(Universe::isTest).findAny().get();
		Modules.remove(Universe.class); // GenerateUniverses adds this
		Modules.put(Universe.class, universe);

		LocalSystem.getInstance(); // Load node.key, after universe
	}


	@AfterClass
	public static void endRadixTest() throws ModuleException {
		safelyStop(Modules.get(Time.class));

		Modules.remove(Universe.class);
		Modules.remove(Time.class);
		Modules.remove(Serialization.class);
		Modules.remove(CommandLine.class);
		Modules.remove(PersistedProperties.class);
		Modules.remove(RuntimeProperties.class);
		Modules.remove(SecureRandom.class);
	}

	private static void safelyStop(Module m) throws ModuleException {
		if (m != null) {
			Modules.getInstance().stop(m);
		}
	}

	protected void addTemporalVertex(Atom atom) throws CryptoException, ValidationException {
		TemporalProof proof = atom.getTemporalProof();
		ECKeyPair nodeKey = LocalSystem.getInstance().getKeyPair();
		ECPublicKey nodePublicKey = nodeKey.getPublicKey();
		if (!proof.hasVertexByNID(nodePublicKey.getUID())) {
			List<TemporalVertex> vertices = proof.getVertices();
			EUID prev = vertices.isEmpty() ? EUID.ZERO : vertices.get(vertices.size() - 1).getHID();
			TemporalVertex vertex = new TemporalVertex(nodePublicKey, clock++, System.currentTimeMillis(), Hash.ZERO_HASH, prev);
			atom.getTemporalProof().add(vertex, nodeKey);
		}
	}
}
