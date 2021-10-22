package com.radixdlt.test.utils;

import com.radixdlt.application.tokens.TokenUtils;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.exception.PrivateKeyException;
import com.radixdlt.crypto.exception.PublicKeyException;
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

import static org.awaitility.Awaitility.await;

/**
 * Various testing utilities
 */
public final class TestingUtils {

    public static final Duration MAX_TIME_TO_WAIT_FOR_NODES_UP = Durations.TWO_MINUTES;

    private static final Logger logger = LogManager.getLogger();

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
        await().atMost(MAX_TIME_TO_WAIT_FOR_NODES_UP).ignoreExceptions().until(() -> {
            TestingUtils.sleepMillis(250);
            RadixHttpClient.HealthStatus status = httpClient.getHealthStatus(rootUrl);
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
     * used for pretty printing
     */
    public static String fromMinorToMajorString(UInt256 minorAmount) {
        double majorAmount = Double.parseDouble(minorAmount.toString()) / Math.pow(10, TokenUtils.SUB_UNITS_POW_10);
        return String.valueOf(majorAmount);
    }

    public static <R> R toTestFailureException(Failure failure) {
        throw new TestFailureException(failure.message());
    }

}
