package org.radix;

import com.radixdlt.consensus.Consensus;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.api.http.RadixHttpServer;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.AddressBookFactory;
import org.radix.network2.addressbook.PeerManager;
import org.radix.network2.addressbook.PeerManagerFactory;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageCentralFactory;
import org.radix.properties.RuntimeProperties;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;
import org.radix.utils.SystemMetaData;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.security.Security;

public final class Radix
{
	static
	{
		java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
	}

	private static final Logger log = Logging.getLogger();

	public static final int 	PROTOCOL_VERSION 		= 100;

	public static final int 	AGENT_VERSION 			= 2710000;
	public static final int 	MAJOR_AGENT_VERSION 	= 2709999;
	public static final int 	REFUSE_AGENT_VERSION 	= 2709999;
	public static final String 	AGENT 					= "/Radix:/"+AGENT_VERSION;

	private RadixEngineAtomProcessor atomProcessor;
	private RadixHttpServer httpServer;

	public static void main(String[] args) {
		try {
			dumpExecutionLocation();
			// Bouncy Castle is required for loading the node key, so set it up now.
			setupBouncyCastle();

			RuntimeProperties properties = loadProperties(args);
			new Radix(properties);
		} catch (Exception ex) {
			log.fatal("Unable to start", ex);
			java.lang.System.exit(-1);
		}
	}

	public Radix(RuntimeProperties properties) {
		// set up serialisation
		Serialization serialization = Serialization.getDefault();
		LocalSystem localSystem = LocalSystem.restoreOrCreate(properties);

		// set up universe
		Universe universe = extractUniverseFrom(properties, serialization);
		Modules.put(Universe.class, universe);

		// set up time services
		Time.start(properties);

		// start events
		Events.getInstance();

		// start database environment
		DatabaseEnvironment dbEnv = new DatabaseEnvironment(properties);

		// start profiling
		SystemMetaData.init(dbEnv);

		// set up networking
		MessageCentral messageCentral = createMessageCentral(properties);
		Interfaces interfaces = new Interfaces();
		AddressBook addressBook = createAddressBook(dbEnv);
		Modules.put(AddressBook.class, addressBook);
		BootstrapDiscovery bootstrapDiscovery = new BootstrapDiscovery(properties, universe);
		PeerManager peerManager = createPeerManager(properties, addressBook, messageCentral, Events.getInstance(), bootstrapDiscovery, interfaces, localSystem);
		peerManager.start();

		// TODO Eventually modules should be created using Google Guice injector
		GlobalInjector globalInjector = new GlobalInjector(properties, dbEnv, messageCentral, localSystem);
		Consensus consensus = globalInjector.getInjector().getInstance(Consensus.class);
		// TODO use consensus for application construction (in our case, the engine middleware)

		// start middleware
		atomProcessor = globalInjector.getInjector().getInstance(RadixEngineAtomProcessor.class);
		atomProcessor.start();

		// start API services
		AtomToBinaryConverter atomToBinaryConverter = globalInjector.getInjector().getInstance(AtomToBinaryConverter.class);
		LedgerEntryStore store = globalInjector.getInjector().getInstance(LedgerEntryStore.class);
		httpServer = new RadixHttpServer(store, atomProcessor, atomToBinaryConverter, universe, messageCentral, serialization, properties, localSystem);
		httpServer.start(properties);

		log.info("Node '" + localSystem.getNID() + "' started successfully");
	}

	private static void dumpExecutionLocation() {
		try {
			String jarFile = ClassLoader.getSystemClassLoader().loadClass("org.radix.Radix").getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			System.setProperty("radix.jar", jarFile);

			String jarPath = jarFile;

			if (jarPath.toLowerCase().endsWith(".jar")) {
				jarPath = jarPath.substring(0, jarPath.lastIndexOf("/"));
			}
			System.setProperty("radix.jar.path", jarPath);

			log.debug("Execution file: "+ System.getProperty("radix.jar"));
			log.debug("Execution path: "+ System.getProperty("radix.jar.path"));
		} catch (URISyntaxException | ClassNotFoundException e) {
			throw new RuntimeException("while fetching execution location", e);
		}
	}

	private Universe extractUniverseFrom(RuntimeProperties properties, Serialization serialization) {
		try {
			byte[] bytes = Bytes.fromBase64String(properties.get("universe"));
			return serialization.fromDson(bytes, Universe.class);
		} catch (SerializationException e) {
			throw new RuntimeException("while deserialising universe", e);
		}
	}

	public void stop() {
		httpServer.stop();

		try {
			atomProcessor.stop();
		} catch (Exception e) {
			throw new RuntimeException("Failure turning off AtomProcessor", e);
		}
	}

	private static void setupBouncyCastle() throws ClassNotFoundException, IllegalAccessException {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
		try {
			Field isRestricted = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");

			log.info("Encryption restrictions are set, need to override...");

			if (Modifier.isFinal(isRestricted.getModifiers())) {
				Field modifiers = Field.class.getDeclaredField("modifiers");
				modifiers.setAccessible(true);
				modifiers.setInt(isRestricted, isRestricted.getModifiers() & ~Modifier.FINAL);
			}
			isRestricted.setAccessible(true);
			isRestricted.setBoolean(null, false);
			isRestricted.setAccessible(false);
			log.info("...override success!");
		} catch (NoSuchFieldException nsfex) {
			log.error("No such field - isRestricted");
		}
	}

	private static RuntimeProperties loadProperties(String[] args) throws IOException, ParseException {
		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null) {
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));
		}

		return new RuntimeProperties(runtimeConfigurationJSON, args);
	}

	private MessageCentral createMessageCentral(RuntimeProperties properties) {
		return new MessageCentralFactory().createDefault(properties);
	}

	private AddressBook createAddressBook(DatabaseEnvironment dbEnv) {
		return new AddressBookFactory().createDefault(dbEnv);
	}

	private PeerManager createPeerManager(RuntimeProperties properties, AddressBook addressBook, MessageCentral messageCentral, Events events, BootstrapDiscovery bootstrapDiscovery, Interfaces interfaces, LocalSystem localSystem) {
		return new PeerManagerFactory().createDefault(properties, addressBook, messageCentral, events, bootstrapDiscovery, interfaces, localSystem);
	}

}
