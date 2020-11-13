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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.utils.MemoryLeakDetector;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.systeminfo.InMemorySystemInfoRunner;
import com.radixdlt.mempool.MempoolReceiver;
import com.radixdlt.network.addressbook.PeerManager;
import com.radixdlt.properties.RuntimeProperties;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import org.radix.api.http.RadixHttpServer;
import org.radix.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.Properties;

public final class Radix {
	private static final String SYSTEM_VERSION_DISPLAY;
	private static final String SYSTEM_VERSION_BRANCH;
	private static final String SYSTEM_VERSION_COMMIT;
	private static final ImmutableMap<String, Object> SYSTEM_VERSION_INFO;

	public static final int 	PROTOCOL_VERSION 		= 100;

	public static final int 	AGENT_VERSION 			= 2710000;
	public static final int 	MAJOR_AGENT_VERSION 	= 2709999;
	public static final int 	REFUSE_AGENT_VERSION 	= 2709999;
	public static final String 	AGENT 					= "/Radix:/"+AGENT_VERSION;

	static {
		System.setProperty("java.net.preferIPv4Stack", "true");

		String branch  = "unknown-branch";
		String commit  = "unknown-commit";
		String display = "unknown-version";
		try (InputStream is = Radix.class.getResourceAsStream("/version.properties")) {
			if (is != null) {
				Properties p = new Properties();
				p.load(is);
				branch  = p.getProperty("VERSION_BRANCH",  branch);
				commit  = p.getProperty("VERSION_COMMIT",  commit);
				display = p.getProperty("VERSION_DISPLAY", display);
			}
		} catch (IOException e) {
			// Ignore exception
		}
		SYSTEM_VERSION_DISPLAY = display;
		SYSTEM_VERSION_BRANCH  = branch;
		SYSTEM_VERSION_COMMIT  = commit;
		SYSTEM_VERSION_INFO = ImmutableMap.of("system_version",
			ImmutableMap.of(
				"branch",           SYSTEM_VERSION_BRANCH,
				"commit",           SYSTEM_VERSION_COMMIT,
				"display",          SYSTEM_VERSION_DISPLAY,
				"agent_version",    AGENT_VERSION,
				"protocol_version", PROTOCOL_VERSION
			)
		);
	}

	private static final Logger log = LogManager.getLogger();

	private static final Object BC_LOCK = new Object();
	private static boolean bcInitialised;

	private static void setupBouncyCastle() {
		synchronized (BC_LOCK) {
			if (bcInitialised) {
				log.warn("Bouncy castle is already initialised");
				return;
			}

			Security.insertProviderAt(new BouncyCastleProvider(), 1);
			bcInitialised = true;
		}
	}

	public static void main(String[] args) {
		try {
			new MemoryLeakDetector();

			logVersion();
			dumpExecutionLocation();
			// Bouncy Castle is required for loading the node key, so set it up now.
			setupBouncyCastle();

			RuntimeProperties properties = loadProperties(args);
			start(properties);
		} catch (Exception ex) {
			log.fatal("Unable to start", ex);
			LogManager.shutdown(); // Flush any async logs
			java.lang.System.exit(-1);
		}
	}

	private static void logVersion() {
		log.always().log("Radix distributed ledger '{}' from branch '{}' commit '{}'",
			SYSTEM_VERSION_DISPLAY, SYSTEM_VERSION_BRANCH, SYSTEM_VERSION_COMMIT);
	}

	public static ImmutableMap<String, Object> systemVersionInfo() {
		return SYSTEM_VERSION_INFO;
	}

	public static void start(RuntimeProperties properties) {
		// TODO Eventually modules should be created using Google Guice injector
		GlobalInjector globalInjector = new GlobalInjector(properties);

		// setup networking
		final PeerManager peerManager = globalInjector.getInjector().getInstance(PeerManager.class);
		peerManager.start();

		// Start mempool receiver
		final MempoolReceiver mempoolReceiver = globalInjector.getInjector().getInstance(MempoolReceiver.class);
		mempoolReceiver.start();

		final InMemorySystemInfoRunner infoStateRunner = globalInjector.getInjector().getInstance(InMemorySystemInfoRunner.class);
		infoStateRunner.start();

		final Map<String, ModuleRunner> moduleRunners = globalInjector.getInjector()
			.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() { }));
		final ModuleRunner consensusRunner = moduleRunners.get("consensus");
		final ModuleRunner syncRunner = moduleRunners.get("sync");
		syncRunner.start();

		// start API services
		final RadixHttpServer httpServer = globalInjector.getInjector().getInstance(RadixHttpServer.class);
		httpServer.start();

		final BFTNode self = globalInjector.getInjector().getInstance(Key.get(BFTNode.class, Self.class));
		if (properties.get("consensus.start_on_boot", true)) {
			consensusRunner.start();
			log.info("Node '{}' started successfully", self);
		} else {
			log.info("Node '{}' ready, waiting for start signal", self);
		}
	}

	private static void dumpExecutionLocation() {
		try {
			String jarFile = Radix.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			System.setProperty("radix.jar", jarFile);

			String jarPath = jarFile;

			if (jarPath.toLowerCase().endsWith(".jar")) {
				jarPath = jarPath.substring(0, jarPath.lastIndexOf('/'));
			}
			System.setProperty("radix.jar.path", jarPath);

			log.debug("Execution file: {}", System.getProperty("radix.jar"));
			log.debug("Execution path: {}", System.getProperty("radix.jar.path"));
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Error while fetching execution location", e);
		}
	}

	private static RuntimeProperties loadProperties(String[] args) throws IOException, ParseException {
		JSONObject runtimeConfigurationJSON = new JSONObject();
		try (InputStream is = Radix.class.getResourceAsStream("/runtime_options.json")) {
			if (is != null) {
				runtimeConfigurationJSON = new JSONObject(IOUtils.toString(is));
			}
		}
		return new RuntimeProperties(runtimeConfigurationJSON, args);
	}
}
