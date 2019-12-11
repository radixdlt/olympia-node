package org.radix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.security.Security;

import com.radixdlt.consensus.Consensus;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.LedgerEntryStore;
import org.apache.commons.cli.CommandLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.api.http.RadixHttpServer;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStopException;
import org.radix.network.Interfaces;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.AddressBookFactory;
import org.radix.network2.addressbook.PeerManager;
import org.radix.network2.addressbook.PeerManagerFactory;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageCentralFactory;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;
import org.radix.utils.SystemMetaData;
import org.radix.utils.SystemProfiler;
import com.radixdlt.utils.Bytes;

public class Radix extends Plugin
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

	/**
	 * @param args
	 */
	public static void main(String[] args)
	{
		try
		{
			JSONObject runtimeConfigurationJSON = new JSONObject();
			if (Radix.class.getResourceAsStream("/runtime_options.json") != null)
				runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));

			RuntimeProperties runtimeProperties = new RuntimeProperties(runtimeConfigurationJSON, args);
			Modules.put(RuntimeProperties.class, runtimeProperties);
			Modules.put(PersistedProperties.class, runtimeProperties);
			Modules.put(CommandLine.class, runtimeProperties.getCommandLine());

			// Setup bouncy castle
			// This is used when loading the node key below, so set it up now.
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

			// Used everywhere, so put it in early.
			Modules.put(SecureRandom.class, new SecureRandom());

			new Radix();
		}
		catch (Exception ex)
		{
			log.fatal("Unable to continue", ex);
			java.lang.System.exit(-1);
		}
	}

	public Radix() throws ModuleException
	{
		start();
	}

	@Override
	public void start_impl() throws ModuleException
	{
		RuntimeProperties properties = Modules.get(RuntimeProperties.class);
		dumpExecutionLocation();

		// set up serialisation
		Serialization serialization = Serialization.getDefault();
		Modules.put(Serialization.class, serialization);

		// set up universe
		Universe universe = extractUniverseFrom(properties);
		Modules.put(Universe.class, universe);

		// set up time services
		Time.start(properties);

		// start events
		Events.getInstance();

		// start database environment
		DatabaseEnvironment dbEnv = new DatabaseEnvironment();
		dbEnv.start();
		Modules.put(DatabaseEnvironment.class, dbEnv);

		// start profiling
		SystemMetaData systemMetaData = new SystemMetaData();
		systemMetaData.start();
		Modules.put(SystemMetaData.class, systemMetaData);
		SystemProfiler systemProfiler = SystemProfiler.getInstance();
		systemProfiler.start();
		Modules.put(SystemProfiler.class, systemProfiler);

		// set up networking
		MessageCentral messageCentral = createMessageCentral(properties);
		Modules.put(MessageCentral.class, messageCentral);
		Modules.put(Interfaces.class, new Interfaces());
		AddressBook addressBook = createAddressBook();
		Modules.put(AddressBook.class, addressBook);
		BootstrapDiscovery bootstrapDiscovery = BootstrapDiscovery.getInstance();
		PeerManager peerManager = createPeerManager(properties, addressBook, messageCentral, Events.getInstance(), bootstrapDiscovery);
		Modules.getInstance().start(peerManager);

		// TODO Eventually modules should be created using Google Guice injector
		GlobalInjector globalInjector = new GlobalInjector();
		Consensus consensus = globalInjector.getInjector().getInstance(Consensus.class);
		// TODO use consensus for application construction (in our case, the engine middleware)

		// start middleware
		atomProcessor = globalInjector.getInjector().getInstance(RadixEngineAtomProcessor.class);
		atomProcessor.start();

		// start API services
		AtomToBinaryConverter atomToBinaryConverter = globalInjector.getInjector().getInstance(AtomToBinaryConverter.class);
		LedgerEntryStore store = globalInjector.getInjector().getInstance(LedgerEntryStore.class);
		httpServer = new RadixHttpServer(store, atomProcessor, atomToBinaryConverter, universe, serialization);
		httpServer.start(properties);

		// start all services
		Modules.getInstance().start();

		log.info("Node '" + LocalSystem.getInstance().getNID() + "' started successfully");
	}

	private void dumpExecutionLocation() {
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

	private Universe extractUniverseFrom(RuntimeProperties properties) {
		try {
			byte[] bytes = Bytes.fromBase64String(properties.get("universe"));
			return Modules.get(Serialization.class).fromDson(bytes, Universe.class);
		} catch (SerializationException e) {
			throw new RuntimeException("while deserialising universe", e);
		}
	}

	@Override
	public void stop_impl() throws ModuleException {
		httpServer.stop();

		/*
		 * Middleware
		 */
		try {
			atomProcessor.stop();
		} catch (Exception e) {
			throw new ModuleStopException("Failure turning off AtomProcessor", e, this);
		}
	}

	private MessageCentral createMessageCentral(RuntimeProperties properties) {
		return new MessageCentralFactory().createDefault(properties);
	}

	private AddressBook createAddressBook() {
		return new AddressBookFactory().createDefault();
	}

	private PeerManager createPeerManager(RuntimeProperties properties, AddressBook addressBook, MessageCentral messageCentral, Events events, BootstrapDiscovery bootstrapDiscovery) {
		return new PeerManagerFactory().createDefault(properties, addressBook, messageCentral, events, bootstrapDiscovery);
	}

}
