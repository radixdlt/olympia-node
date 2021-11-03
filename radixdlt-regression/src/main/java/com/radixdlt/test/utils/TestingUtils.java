package com.radixdlt.test.utils;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;

/**
 * Various testing utilities
 */
public final class TestingUtils {

    private static final Logger logger = LogManager.getLogger();

    public static final Duration MAX_TIME_TO_WAIT_FOR_NODES_UP = Durations.TWO_MINUTES;

    private TestingUtils() {

    }

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

    /**
     * Will wait until the native token balance reaches the given amount
     */
    public static void waitForBalanceToReach(Account account, UInt256 amount) {
        try {
            await().atMost(Durations.ONE_MINUTE).until(() ->
                account.getOwnNativeTokenBalance().getAmount().compareTo(amount) >= 0);
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Account's balance did not reach " + amount);
        }
    }

    /**
     * Will wait until the account's native token balance increases by any amount
     */
    public static void waitForBalanceToIncrease(Account account) {
        UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
        try {
            await().atMost(Durations.ONE_MINUTE).until(() ->
                account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) > 0);
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Account's balance did not decrease");
        }
    }

    /**
     * Waits until the account's native balance decreases by any amount
     */
    public static void waitForBalanceToDecrease(Account account) {
        UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
        try {
            await().atMost(Durations.ONE_MINUTE).until(() ->
                account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) < 0);
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Account's balance did not decrease");
        }
    }

    /**
     * Waits until the account's native balance changes, up or down
     */
    public static void waitForBalanceToChange(Account account) {
        UInt256 initialAmount = account.getOwnNativeTokenBalance().getAmount();
        try {
            await().atMost(Durations.ONE_MINUTE).until(() ->
                account.getOwnNativeTokenBalance().getAmount().compareTo(initialAmount) != 0);
        } catch (ConditionTimeoutException e) {
            throw new TestFailureException("Account's balance did not change");
        }
    }

    /**
     * Uses a default duration (might want to externalize this)
     */
    public static void waitForNodeToBeUp(RadixHttpClient httpClient, String rootUrl) {
        await().pollDelay(Durations.TWO_HUNDRED_MILLISECONDS).atMost(MAX_TIME_TO_WAIT_FOR_NODES_UP).ignoreExceptions().until(() -> {
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

    /**
     * expects a token creation AID
     */
    public static TokenInfo getTokenInfoFromAID(Account account, AID aid) {
        var transaction = account.lookup(aid);
        return getTokenInfoFromTransaction(account, transaction);
    }

    /**
     * expects a token creation AID
     */
    public static String getRriFromAID(Account account, AID aid) {
        var transaction = account.lookup(aid);
        return getTokenInfoFromTransaction(account, transaction).getRri();
    }

    /**
     * expects a token creation transaction dto
     */
    public static TokenInfo getTokenInfoFromTransaction(Account account, TransactionDTO transaction) {
        if (transaction.getEvents().isEmpty() || transaction.getEvents().get(0).getRri().isEmpty()) {
            throw new TestFailureException("Token creation transaction did not contain the correct event");
        }
        var rriOpt = transaction.getEvents().get(0).getRri();
        var rri = rriOpt.orElseThrow(() -> new TestFailureException("Token creation action did not have an rri"));
        return account.token().describe(rri);
    }

    public static <R> R toTestFailureException(Failure failure) {
        throw new TestFailureException(failure.message());
    }

    /**
     * Waits for the given # of epochs to pass
     */
    public static void waitEpochs(Account account, int numberOfEpochsToWait) {
        IntStream.range(0, numberOfEpochsToWait).forEach(i -> waitUntilEndNextEpoch(account));
    }

    public static void waitUntilEndNextEpoch(Account account) {
        var currentEpoch = account.ledger().epoch().getHeader().getEpoch();
        logger.debug("Waiting for epoch {} to end...", currentEpoch);
        var stopwatch = Stopwatch.createStarted();
        await().pollInterval(Durations.ONE_SECOND).atMost(Duration.ofMinutes(30)).until(() ->
            account.ledger().epoch().getHeader().getEpoch() > currentEpoch
        );
        logger.debug("Epoch {} ended", currentEpoch);
        stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }

}
