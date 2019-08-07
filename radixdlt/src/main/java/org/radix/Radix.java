package org.radix;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.security.Security;

import com.radixdlt.tempo.conflict.LocalConflictResolver;
import com.radixdlt.tempo.Tempo;
import com.radixdlt.tempo.store.TempoAtomStore;
import com.radixdlt.tempo.sync.PeerSupplierAdapter;
import com.radixdlt.tempo.sync.SimpleEdgeSelector;
import com.radixdlt.tempo.sync.TempoAtomSynchroniser;
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
import org.radix.mass.Masses;
import org.radix.modules.Modules;
import org.radix.modules.Plugin;
import org.radix.modules.exceptions.ModuleException;
import org.radix.modules.exceptions.ModuleStartException;
import org.radix.network.Interfaces;
import org.radix.network.Network;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.PeerHandler;
import org.radix.network.peers.PeerStore;
import org.radix.properties.PersistedProperties;
import org.radix.properties.RuntimeProperties;
import org.radix.routing.Routing;
import com.radixdlt.serialization.Serialization;
import org.radix.shards.Shards;
import org.radix.time.Time;
import org.radix.time.RTP.RTPService;
import com.radixdlt.universe.Universe;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;
import org.radix.utils.SystemMetaData;
import org.radix.utils.SystemProfiler;
import com.radixdlt.utils.Bytes;
import org.radix.validation.Validation;

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
			Security.addProvider(new BouncyCastleProvider());
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

			log.info("Execution file: "+java.lang.System.getProperty("radix.jar"));
			log.info("Execution path: "+java.lang.System.getProperty("radix.jar.path"));
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Could not set execution information", ex, this);
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
		 * RTP
		 */
		try
		{
			if (!Modules.get(RuntimeProperties.class).has("rtp.disable")) {
				Modules.getInstance().start(new RTPService());
			}
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up RTP", ex, this);
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
		 * VALIDATORS
		 */
		try
		{
			Modules.getInstance().start(new Validation());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Validation", ex, this);
		}

		/*
		 * MASS
		 */
		try
		{
			Modules.getInstance().startIfNeeded(Masses.class);
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Mass", ex, this);
		}

		/*
		 * MESSAGES
		 */
		try
		{
			Modules.getInstance().start(Messaging.getInstance());
//			Modules.getInstance().start(new MessageProfiler());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Messages", ex, this);
		}

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

		if (Modules.get(RuntimeProperties.class).get("tempo2", false)) {
			TempoAtomStore tempoAtomStore = new TempoAtomStore(() -> Modules.get(DatabaseEnvironment.class));
			Modules.getInstance().start(Tempo.from(
				TempoAtomSynchroniser.defaultBuilder(
					tempoAtomStore.asReadOnlyView(),
					LocalSystem.getInstance(),
					Messaging.getInstance(),
					new PeerSupplierAdapter(() -> Modules.get(PeerHandler.class))
				)
				.edgeSelector(new SimpleEdgeSelector())
				.build(),
				tempoAtomStore,
				new LocalConflictResolver(LocalSystem.getInstance().getNID())
			));
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
			Modules.getInstance().start(Network.getInstance());
			Modules.getInstance().start(new PeerStore());
			Modules.getInstance().start(new PeerHandler());
		}
		catch (Exception ex)
		{
			throw new ModuleStartException("Failure setting up Network", ex, this);
		}

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
		 * API
		 */
		try
		{
			Modules.getInstance().start(new API());
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
	public void stop_impl() throws ModuleException { }
}
