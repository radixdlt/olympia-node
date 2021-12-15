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

package com.radixdlt.test.utils;

import static org.awaitility.Awaitility.await;

import com.google.common.base.Stopwatch;
import com.radixdlt.client.lib.dto.TokenInfo;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.network.RadixNode;
import com.radixdlt.test.network.client.RadixHttpClient;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

/** Various testing utilities */
public final class TestingUtils {

  private static final Logger logger = LogManager.getLogger();

  public static final Duration MAX_TIME_TO_WAIT_FOR_NODES_UP = Durations.TWO_MINUTES;

  private TestingUtils() {}

  public static boolean isNullOrEmpty(String string) {
    return string == null || string.isEmpty();
  }

  public static ECKeyPair createKeyPairFromNumber(int privateKey) {
    var pk = new byte[ECKeyPair.BYTES];
    Ints.copyTo(privateKey, pk, ECKeyPair.BYTES - Integer.BYTES);

    try {
      return ECKeyPair.fromPrivateKey(pk);
    } catch (PrivateKeyException | PublicKeyException e) {
      throw new IllegalArgumentException("Error while generating public key", e);
    }
  }

  public static void sleep(int seconds) {
    sleepMillis(1000L * seconds);
  }

  public static void sleepMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new TestFailureException(e);
    }
  }

  public static String getEnvWithDefault(String envName, String defaultValue) {
    String envValue = System.getenv(envName);
    return (envValue == null || envValue.isBlank()) ? defaultValue : envValue;
  }

  public static int getEnvWithDefault(String envName, int defaultValue) {
    String envValue = System.getenv(envName);
    return (envValue == null || envValue.isBlank()) ? defaultValue : Integer.parseInt(envValue);
  }

  /** Will wait until the native token balance reaches the given amount */
  public static void waitForBalanceToReach(Account account, UInt256 amount) {
    try {
      await()
          .atMost(Durations.ONE_MINUTE)
          .until(() -> account.getOwnNativeTokenBalance().getAmount().compareTo(amount) >= 0);
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException("Account's balance did not reach " + amount);
    }
  }

  /** Will wait until the account's native token balance increases by any amount */
  public static void waitForBalanceToIncrease(Account account) {
    UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
    try {
      await()
          .atMost(Durations.ONE_MINUTE)
          .until(() -> account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) > 0);
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException("Account's balance did not decrease");
    }
  }

  /** Waits until the account's native balance decreases by any amount */
  public static void waitForBalanceToDecrease(Account account) {
    UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
    try {
      await()
          .atMost(Durations.ONE_MINUTE)
          .until(() -> account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) < 0);
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException("Account's balance did not decrease");
    }
  }

  /** Waits until the account's native balance changes, up or down */
  public static void waitForBalanceToChange(Account account) {
    UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
    try {
      await()
          .atMost(Durations.ONE_MINUTE)
          .until(
              () -> account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) != 0);
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException("Account's balance did not change");
    }
  }

  /** Uses a default duration (might want to externalize this) */
  public static void waitForNodeToBeUp(RadixHttpClient httpClient, String rootUrl) {
    await()
        .pollDelay(Durations.TWO_HUNDRED_MILLISECONDS)
        .atMost(MAX_TIME_TO_WAIT_FOR_NODES_UP)
        .ignoreExceptions()
        .until(
            () -> {
              var status = httpClient.getHealthStatus(rootUrl);
              if (!status.equals(RadixHttpClient.HealthStatus.UP)) {
                return false;
              }
              logger.debug("Node at {} is UP", rootUrl);
              return true;
            });
  }

  public static void waitForNodeToBeUp(RadixNode node, RadixNetworkConfiguration configuration) {
    String healthRootUrl = node.getRootUrl() + ":" + node.getSecondaryPort();
    waitForNodeToBeUp(RadixHttpClient.fromRadixNetworkConfiguration(configuration), healthRootUrl);
  }

  /** expects a token creation AID */
  public static TokenInfo getTokenInfoFromAID(Account account, AID aid) {
    var transaction = account.lookup(aid);
    return getTokenInfoFromTransaction(account, transaction);
  }

  /** expects a token creation AID */
  public static String getRriFromAID(Account account, AID aid) {
    var transaction = account.lookup(aid);
    return getTokenInfoFromTransaction(account, transaction).getRri();
  }

  /** expects a token creation transaction dto */
  public static TokenInfo getTokenInfoFromTransaction(Account account, TransactionDTO transaction) {
    if (transaction.getEvents().isEmpty() || transaction.getEvents().get(0).getRri().isEmpty()) {
      throw new TestFailureException(
          "Token creation transaction did not contain the correct event");
    }
    var rriOpt = transaction.getEvents().get(0).getRri();
    var rri =
        rriOpt.orElseThrow(
            () -> new TestFailureException("Token creation action did not have an rri"));
    return account.token().describe(rri);
  }

  public static <R> R toTestFailureException(Failure failure) {
    throw new TestFailureException(failure.message());
  }

  /** Waits for the given # of epochs to pass */
  public static void waitEpochs(Account account, int numberOfEpochsToWait) {
    IntStream.range(0, numberOfEpochsToWait).forEach(i -> waitUntilEndNextEpoch(account));
  }

  public static void waitUntilEndNextEpoch(Account account) {
    var currentEpoch = account.ledger().epoch().getHeader().getEpoch();
    logger.debug("Waiting for epoch {} to end...", currentEpoch);
    var stopwatch = Stopwatch.createStarted();
    await()
        .pollInterval(Durations.ONE_SECOND)
        .atMost(Duration.ofMinutes(30))
        .until(() -> account.ledger().epoch().getHeader().getEpoch() > currentEpoch);
    logger.debug("Epoch {} ended", currentEpoch);
    stopwatch.elapsed(TimeUnit.MILLISECONDS);
  }
}
