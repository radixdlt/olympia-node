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

package com.radixdlt.store;

import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_CHECKPOINTER;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_CLEANER;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_EVICTOR;
import static com.sleepycat.je.EnvironmentConfig.ENV_RUN_VERIFIER;
import static com.sleepycat.je.EnvironmentConfig.LOG_FILE_CACHE_SIZE;
import static com.sleepycat.je.EnvironmentConfig.TREE_MAX_EMBEDDED_LN;

import com.google.inject.Inject;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import java.io.File;
import java.text.StringCharacterIterator;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DatabaseEnvironment {
  private static final Logger log = LogManager.getLogger();

  private Environment environment;

  @Inject
  public DatabaseEnvironment(
      @DatabaseLocation String databaseLocation, @DatabaseCacheSize long cacheSize) {
    var dbHome = new File(databaseLocation);
    dbHome.mkdir();

    System.setProperty("je.disable.java.adler32", "true");

    EnvironmentConfig environmentConfig = new EnvironmentConfig();
    environmentConfig.setTransactional(true);
    environmentConfig.setAllowCreate(true);
    environmentConfig.setLockTimeout(30, TimeUnit.SECONDS);
    environmentConfig.setDurability(Durability.COMMIT_SYNC);
    environmentConfig.setConfigParam(LOG_FILE_CACHE_SIZE, "256");
    environmentConfig.setConfigParam(ENV_RUN_CHECKPOINTER, "true");
    environmentConfig.setConfigParam(ENV_RUN_CLEANER, "true");
    environmentConfig.setConfigParam(ENV_RUN_EVICTOR, "true");
    environmentConfig.setConfigParam(ENV_RUN_VERIFIER, "false");
    environmentConfig.setConfigParam(TREE_MAX_EMBEDDED_LN, "0");
    environmentConfig.setCacheSize(cacheSize);
    environmentConfig.setCacheMode(CacheMode.EVICT_LN);

    environment = new Environment(dbHome, environmentConfig);

    log.info("DB cache size set to {} ({} bytes)", toHumanReadable(cacheSize), cacheSize);
  }

  public void stop() {
    try {
      environment.close();
    } catch (DatabaseException e) {
      log.error("Error while closing database. Possible DB corruption.");
    }
    environment = null;
  }

  public Environment getEnvironment() {
    if (environment == null) {
      throw new IllegalStateException("environment is not started");
    }

    return environment;
  }

  private static String toHumanReadable(long bytes) {
    var absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);

    if (absB < 1024) {
      return bytes + " B";
    }

    var value = absB;
    var ci = new StringCharacterIterator("KMGTPE");

    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
      value >>= 10;
      ci.next();
    }

    value *= Long.signum(bytes);
    return String.format("%.1f %ciB", value / 1024.0, ci.current());
  }
}
