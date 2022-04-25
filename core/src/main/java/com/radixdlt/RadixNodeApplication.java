/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.radixdlt.modules.ModuleRunner;
import com.radixdlt.modules.RadixNodeModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.monitoring.SystemCounters;
import com.radixdlt.environment.Runners;
import com.radixdlt.network.p2p.transport.PeerServerBootstrap;
import com.radixdlt.utils.properties.RuntimeProperties;
import com.radixdlt.store.berkeley.BerkeleyAddressBookPersistence;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.store.berkeley.BerkeleySafetyStateStore;
import com.radixdlt.utils.MemoryLeakDetector;
import io.undertow.Undertow;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONObject;
import com.radixdlt.utils.IOUtils;

public final class RadixNodeApplication {
  private static final Logger log = LogManager.getLogger();

  private RadixNodeApplication() {}

  private static final String SYSTEM_VERSION_DISPLAY;
  private static final String SYSTEM_VERSION_BRANCH;
  private static final String SYSTEM_VERSION_COMMIT;
  private static final Map<String, Map<String, Object>> SYSTEM_VERSION_INFO;

  public static final String SYSTEM_VERSION_KEY = "system_version";
  public static final String VERSION_STRING_KEY = "version_string";

  static {
    System.setProperty("java.net.preferIPv4Stack", "true");

    var branch = "unknown-branch";
    var commit = "unknown-commit";
    var display = "unknown-version";
    var map = new HashMap<String, Object>();

    try (var is = RadixNodeApplication.class.getResourceAsStream("/version.properties")) {
      if (is != null) {
        var p = new Properties();
        p.load(is);
        branch = p.getProperty("VERSION_BRANCH", branch);
        commit = p.getProperty("VERSION_COMMIT", commit);
        display = p.getProperty("VERSION_DISPLAY", display);

        for (var key : p.stringPropertyNames()) {
          var mapKey = key.split("_", 2)[1].toLowerCase(Locale.US);
          var defaultValue = "unknown-" + mapKey;

          map.put(mapKey, p.getProperty(key, defaultValue));
        }
      }
    } catch (IOException e) {
      // Ignore exception
    }

    SYSTEM_VERSION_DISPLAY = display;
    SYSTEM_VERSION_BRANCH = branch;
    SYSTEM_VERSION_COMMIT = commit;

    map.put(VERSION_STRING_KEY, calculateVersionString(map));

    SYSTEM_VERSION_INFO = Map.of(SYSTEM_VERSION_KEY, Map.copyOf(map));
  }

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
      MemoryLeakDetector.start();

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
    log.always()
        .log(
            "Radix distributed ledger '{}' from branch '{}' commit '{}'",
            SYSTEM_VERSION_DISPLAY,
            SYSTEM_VERSION_BRANCH,
            SYSTEM_VERSION_COMMIT);
  }

  public static Map<String, Map<String, Object>> systemVersionInfo() {
    return SYSTEM_VERSION_INFO;
  }

  public static void start(RuntimeProperties properties) {
    long start = System.currentTimeMillis();
    var injector = Guice.createInjector(new RadixNodeModule(properties));

    final Map<String, ModuleRunner> moduleRunners =
        injector.getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() {}));

    final var p2pNetworkRunner = moduleRunners.get(Runners.P2P_NETWORK);
    p2pNetworkRunner.start();

    final var systemInfoRunner = moduleRunners.get(Runners.SYSTEM_INFO);
    systemInfoRunner.start();

    final var syncRunner = moduleRunners.get(Runners.SYNC);
    syncRunner.start();

    final var mempoolReceiverRunner = moduleRunners.get(Runners.MEMPOOL);
    mempoolReceiverRunner.start();

    final var peerServer = injector.getInstance(PeerServerBootstrap.class);
    try {
      peerServer.start();
    } catch (InterruptedException e) {
      log.error("Cannot start p2p server", e);
    }

    final var undertow = injector.getInstance(Undertow.class);
    undertow.start();

    final var consensusRunner = moduleRunners.get(Runners.CONSENSUS);
    consensusRunner.start();

    final BFTNode self = injector.getInstance(Key.get(BFTNode.class, Self.class));
    long finish = System.currentTimeMillis();
    var systemCounters = injector.getInstance(SystemCounters.class);
    systemCounters.set(SystemCounters.CounterType.STARTUP_TIME_MS, finish - start);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(injector)));

    log.info("Node '{}' started successfully in {} seconds", self, (finish - start) / 1000);
  }

  private static void shutdown(Injector injector) {
    // using System.out.println as logger no longer works reliably in a shutdown hook
    final var self = injector.getInstance(Key.get(BFTNode.class, Self.class));
    System.out.println("Node " + self + " is shutting down...");

    injector
        .getInstance(Key.get(new TypeLiteral<Map<String, ModuleRunner>>() {}))
        .forEach((k, moduleRunner) -> moduleRunner.stop());

    try {
      injector.getInstance(BerkeleyAddressBookPersistence.class).close();
    } catch (Exception e) {
      // no-op
    }

    try {
      injector.getInstance(BerkeleySafetyStateStore.class).close();
    } catch (Exception e) {
      // no-op
    }

    try {
      injector.getInstance(BerkeleyLedgerEntryStore.class).close();
    } catch (Exception e) {
      // no-op
    }

    System.out.println("Node shutdown completed");
  }

  private static void dumpExecutionLocation() {
    try {
      String jarFile =
          RadixNodeApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
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

  private static RuntimeProperties loadProperties(String[] args)
      throws IOException, ParseException {
    JSONObject runtimeConfigurationJSON = new JSONObject();
    try (InputStream is = RadixNodeApplication.class.getResourceAsStream("/runtime_options.json")) {
      if (is != null) {
        runtimeConfigurationJSON = new JSONObject(IOUtils.toString(is));
      }
    }
    return new RuntimeProperties(runtimeConfigurationJSON, args);
  }

  @VisibleForTesting
  static String calculateVersionString(Map<String, Object> details) {
    if (isCleanTag(details)) {
      return lastTag(details);
    } else {
      var version =
          branchName(details) == null
              ? "detached-head-" + gitHash(details)
              : (lastTag(details) + "-" + branchName(details)).replace('/', '~')
                  + "-"
                  + gitHash(details);

      return version;
    }
  }

  private static boolean isCleanTag(Map<String, Object> details) {
    return Objects.equals(details.get("tag"), details.get("last_tag"));
  }

  private static String lastTag(Map<String, Object> details) {
    return (String) details.get("last_tag");
  }

  private static String gitHash(Map<String, Object> details) {
    return (String) details.get("build");
  }

  private static String branchName(Map<String, Object> details) {
    return (String) details.get("branch");
  }
}
