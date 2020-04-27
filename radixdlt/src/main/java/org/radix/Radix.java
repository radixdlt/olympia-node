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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.ChainedBFT;
import com.radixdlt.mempool.MempoolReceiver;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerManager;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Bytes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.api.http.RadixHttpServer;
import org.radix.database.DatabaseEnvironment;
import org.radix.events.Events;
import org.radix.time.Time;
import org.radix.universe.UniverseValidator;
import org.radix.universe.system.LocalSystem;
import org.radix.utils.IOUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.security.Security;

public final class Radix
{
	static
	{
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	private static final Logger log = LogManager.getLogger();

	public static final int 	PROTOCOL_VERSION 		= 100;

	public static final int 	AGENT_VERSION 			= 2710000;
	public static final int 	MAJOR_AGENT_VERSION 	= 2709999;
	public static final int 	REFUSE_AGENT_VERSION 	= 2709999;
	public static final String 	AGENT 					= "/Radix:/"+AGENT_VERSION;

	private static final Object BC_LOCK = new Object();
	private static boolean bcInitialised;

	private static void setupBouncyCastle() throws ClassNotFoundException, IllegalAccessException {
		synchronized (BC_LOCK) {
			if (bcInitialised) {
				log.warn("Bouncy castle is already initialised");
				return;
			}

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

			bcInitialised = true;
		}
	}

	public static void main(String[] args) {
		try {
			dumpExecutionLocation();
			// Bouncy Castle is required for loading the node key, so set it up now.
			setupBouncyCastle();

			RuntimeProperties properties = loadProperties(args);
			start(properties);
		} catch (Exception ex) {
			log.fatal("Unable to start", ex);
			java.lang.System.exit(-1);
		}
	}

	public static void start(RuntimeProperties properties) {
		Serialization serialization = DefaultSerialization.getInstance();
		Universe universe = extractUniverseFrom(properties, serialization);

		// set up time services
		Time.start(properties);

		// start events
		Events.getInstance();

		// start database environment
		DatabaseEnvironment dbEnv = new DatabaseEnvironment(properties);

		// TODO Eventually modules should be created using Google Guice injector
		GlobalInjector globalInjector = new GlobalInjector(properties, dbEnv, universe);
		// TODO use consensus for application construction (in our case, the engine middleware)

		// setup networking
		AddressBook addressBook = globalInjector.getInjector().getInstance(AddressBook.class);
		PeerManager peerManager = globalInjector.getInjector().getInstance(PeerManager.class);
		LocalSystem localSystem = globalInjector.getInjector().getInstance(LocalSystem.class);
		peerManager.start();

		// Start mempool receiver
		globalInjector.getInjector().getInstance(MempoolReceiver.class).start();

		// start API services
		ChainedBFT bft = globalInjector.getInjector().getInstance(ChainedBFT.class);
		bft.processEvents()
			.subscribe(
				event -> {
				},
				e -> {
					log.error("BFT Unexpected Error", e);
					System.exit(-1);
				}
			);

		SubmissionControl submissionControl = globalInjector.getInjector().getInstance(SubmissionControl.class);
		AtomToBinaryConverter atomToBinaryConverter = globalInjector.getInjector().getInstance(AtomToBinaryConverter.class);
		LedgerEntryStore store = globalInjector.getInjector().getInstance(LedgerEntryStore.class);
		RadixHttpServer httpServer = new RadixHttpServer(store, submissionControl, atomToBinaryConverter, universe, serialization, properties, localSystem, addressBook);
		httpServer.start(properties);

		log.info("Node '{}' started successfully", localSystem.getNID());
	}

	private static void dumpExecutionLocation() {
		try {
			String jarFile = ClassLoader.getSystemClassLoader().loadClass(Radix.class.getName()).getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			System.setProperty("radix.jar", jarFile);

			String jarPath = jarFile;

			if (jarPath.toLowerCase().endsWith(".jar")) {
				jarPath = jarPath.substring(0, jarPath.lastIndexOf('/'));
			}
			System.setProperty("radix.jar.path", jarPath);

			log.debug("Execution file: {}", System.getProperty("radix.jar"));
			log.debug("Execution path: {}", System.getProperty("radix.jar.path"));
		} catch (URISyntaxException | ClassNotFoundException e) {
			throw new IllegalStateException("Error while fetching execution location", e);
		}
	}

	private static Universe extractUniverseFrom(RuntimeProperties properties, Serialization serialization) {
		try {
			byte[] bytes = Bytes.fromBase64String(properties.get("universe"));
			Universe u = serialization.fromDson(bytes, Universe.class);
			UniverseValidator.validate(u);
			return u;
		} catch (SerializationException e) {
			throw new IllegalStateException("Error while deserialising universe", e);
		}
	}

	private static RuntimeProperties loadProperties(String[] args) throws IOException, ParseException {
		JSONObject runtimeConfigurationJSON = new JSONObject();
		if (Radix.class.getResourceAsStream("/runtime_options.json") != null) {
			runtimeConfigurationJSON = new JSONObject(IOUtils.toString(Radix.class.getResourceAsStream("/runtime_options.json")));
		}

		return new RuntimeProperties(runtimeConfigurationJSON, args);
	}
}
