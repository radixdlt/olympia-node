package org.radix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.security.Security;

import com.radixdlt.consensus.Consensus;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.consensus.tempo.Tempo;
import com.radixdlt.store.LedgerEntryStore;
import org.apache.commons.cli.CommandLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.api.API;
import org.radix.atoms.Atoms;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.EventProfiler;
import org.radix.events.Events;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Module;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.modules.exceptions.ModuleStopException;
import org.radix.network.Interfaces;
import org.radix.network.discovery.BootstrapDiscovery;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.AddressBookFactory;
import org.radix.network2.addressbook.PeerManagerFactory;
import org.radix.network2.messaging.MessageCentral;
import org.radix.network2.messaging.MessageCentralFactory;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import org.radix.routing.Routing;
import com.radixdlt.serialization.Serialization;
import org.radix.shards.Shards;
import org.radix.time.Time;
import com.radixdlt.universe.Universe;
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
	private AtomToBinaryConverter atomToBinaryConverter;
	private Tempo ledger;

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
		Modules.put(Serialization.class, Serialization.getDefault());

		try
		{
			// Setup execution information //
			String jarFile = ClassLoader.getSystemClassLoader().loadClass("org.radix.Radix").getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			java.lang.System.setProperty("radix.jar", jarFile);

			String jarPath = jarFile;

			if (jarPath.toLowerCase().endsWith(".jar"))
				jarPath = jarPath.substring(0, jarPath.lastIndexOf("/"));
			java.lang.System.setProperty("radix.jar.path", jarPath);

			log.debug("Execution file: "+java.lang.System.getProperty("radix.jar"));
			log.debug("Execution path: "+java.lang.System.getProperty("radix.jar.path"));
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Could not set execution information", ex, this);
		}

		/*
		 * UNIVERSE
		 */
		try
		{
			byte[] bytes = Bytes.fromBase64String(Modules.get(RuntimeProperties.class).get("universe"));
			Universe universe = Modules.get(Serialization.class).fromDson(bytes, Universe.class);
			Modules.put(Universe.class, universe);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Universe", ex, this);
		}

		/*
		 * TIME
		 */
		try
		{
			Modules.getInstance().start(new Time());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Time", ex, this);
		}

		/*
		 * EVENTS
		 */
		try
		{
			Events.getInstance();
			Modules.getInstance().startIfNeeded(EventProfiler.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Events", ex, this);
		}

		/*
		 * DATABASE
		 */
		try
		{
			Modules.getInstance().startIfNeeded(DatabaseEnvironment.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up DB", ex, this);
		}

		/*
		 * SYSTEM
		 */
		try
		{
			Modules.getInstance().startIfNeeded(SystemMetaData.class);
			Modules.getInstance().start(SystemProfiler.getInstance());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up metrics and stats", ex, this);
		}

		/*
		 * MESSAGES
		 */
		MessageCentral messageCentral = createMessageCentral(Modules.get(RuntimeProperties.class));
		Modules.put(MessageCentral.class, messageCentral);

		/*
		 * ROUTING
		 */
		try
		{
			Modules.getInstance().startIfNeeded(Routing.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Routing", ex, this);
		}

		/*
		 * ATOMS
		 */
		try
		{
			Modules.getInstance().startIfNeeded(Atoms.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Atoms", ex, this);
		}

		/*
		 * SHARDS
		 */
		try
		{
			Modules.getInstance().startIfNeeded(Shards.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Shards", ex, this);
		}

		if (Modules.get(CommandLine.class).hasOption("genesis"))
			java.lang.System.exit(0);

		/*
		 * NETWORK
		 */
		try
		{
			Modules.getInstance().start(new Interfaces());
			AddressBook addressBook = createAddressBook();
			Modules.put(AddressBook.class, addressBook);
			BootstrapDiscovery bootstrapDiscovery = BootstrapDiscovery.getInstance();
			Modules.getInstance().start(createPeerManager(Modules.get(RuntimeProperties.class), addressBook, messageCentral, Events.getInstance(), bootstrapDiscovery));
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Network", ex, this);
		}

		/*
		 * Eventually modules should be created using Google Guice injector
		 */
		GlobalInjector globalInjector = new GlobalInjector();

		/**
		 * TEMPO
		 */
		ledger = (Tempo) globalInjector.getInjector().getInstance(Consensus.class);
		ledger.start();

		/*
		 * CP
		 */
		try
		{
			Modules.getInstance().start(new RadixServer());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up CP", ex, this);
		}

		/*
		 * Middleware
		 */
		try {
			atomProcessor = globalInjector.getInjector().getInstance(RadixEngineAtomProcessor.class);
			atomProcessor.start();
			atomToBinaryConverter = globalInjector.getInjector().getInstance(AtomToBinaryConverter.class);
		} catch (Exception e) {
			throw new ModuleStartException("Failure setting up AtomProcessor", e, this);
		}

		/*
		 * API
		 */
		try
		{
			LedgerEntryStore store = globalInjector.getInjector().getInstance(LedgerEntryStore.class);
			Modules.getInstance().start(new API(store, atomProcessor, atomToBinaryConverter));
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up API", ex, this);
		}

		// START UP ALL SERVICES //
		Modules.getInstance().start();

		log.info("Node '"+LocalSystem.getInstance().getNID()+"' started successfully");
	}

	@Override
	public void stop_impl() throws ModuleException {

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

	private Module createPeerManager(RuntimeProperties properties, AddressBook addressBook, MessageCentral messageCentral, Events events, BootstrapDiscovery bootstrapDiscovery) {
		return new PeerManagerFactory().createDefault(properties, addressBook, messageCentral, events, bootstrapDiscovery);
	}

}
