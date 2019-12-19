package org.radix.integration;

import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.apache.commons.cli.CommandLine;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.radix.GenerateUniverses;
import org.radix.Radix;
import org.radix.modules.Modules;
import org.radix.properties.RuntimeProperties;
import org.radix.serialization.TestSetupUtils;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;

import java.security.SecureRandom;

public class RadixTest
{
	private static Serialization serialization;
	private static String dbLocation = null;
	private static RuntimeProperties properties;
	private static LocalSystem localSystem;
	private static Universe universe;

	private long clock = 100L; // Arbitrary starting point. Must be larger than number of atoms in genesis.

	@BeforeClass
	public static void startRadixTest() throws Exception {
		TestSetupUtils.installBouncyCastleProvider();

		serialization = Serialization.getDefault();

		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

		properties = new RuntimeProperties(runtimeConfigurationJSON, null);

		properties.set("debug.nopow", true);
		if (dbLocation == null) {
			// Avoid RADIXDB_TEST_TEST_TEST_TEST_TEST situation
			dbLocation = properties.get("db.location", ".//RADIXDB") + "_TEST";
		}
		properties.set("db.location", dbLocation);

		universe = new GenerateUniverses(properties).generateUniverses().stream().filter(Universe::isTest).findAny().get();
		Modules.remove(Universe.class); // GenerateUniverses adds this
		Modules.put(Universe.class, universe);

		localSystem = LocalSystem.restoreOrCreate(properties, universe);// Load node.ks, after universe
	}

	@AfterClass
	public static void endRadixTest() {
		Modules.remove(Universe.class);
		Modules.remove(Time.class);
		Modules.remove(CommandLine.class);
		Modules.remove(SecureRandom.class);
	}

	protected static Serialization getSerialization() {
		return serialization;
	}

	public static RuntimeProperties getProperties() {
		return properties;
	}

	public static LocalSystem getLocalSystem() {
		return localSystem;
	}

	public static Universe getUniverse() {
		return universe;
	}
}
