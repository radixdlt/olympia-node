package org.radix.integration;

import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.radix.GenerateUniverses;
import org.radix.Radix;
import org.radix.network2.transport.udp.PublicInetAddress;
import org.radix.properties.RuntimeProperties;
import org.radix.serialization.TestSetupUtils;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;

import java.util.Objects;

public class RadixTest
{
	private static Serialization serialization;
	private static String dbLocation = null;
	private static RuntimeProperties properties;
	private static LocalSystem localSystem;
	private static Universe universe;

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
		PublicInetAddress.configure(null, universe.getPort());
		localSystem = LocalSystem.restoreOrCreate(properties, universe);// Load node.ks, after universe
	}

	@AfterClass
	public static void stopRadixTest() {
		serialization = null;
		dbLocation = null;
		properties = null;
		localSystem = null;
		universe = null;
	}

	protected static Serialization getSerialization() {
		return Objects.requireNonNull(serialization, "serialization was not initialized");
	}

	public static RuntimeProperties getProperties() {
		return Objects.requireNonNull(properties, "properties was not initialized");
	}

	public static LocalSystem getLocalSystem() {
		return Objects.requireNonNull(localSystem, "localSystem was not initialized");
	}

	public static Universe getUniverse() {
		return Objects.requireNonNull(universe, "universe was not initialized");
	}
}
