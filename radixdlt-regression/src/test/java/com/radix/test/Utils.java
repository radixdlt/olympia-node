package com.radix.test;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.AccountAddressing;
import com.radixdlt.identifiers.ValidatorAddressing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

import com.radix.test.account.Account;
import com.radixdlt.application.tokens.TokenUtils;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;

import static org.awaitility.Awaitility.await;

/**
 * Various testing utilities
 */
public final class Utils {

    private static final Logger logger = LogManager.getLogger();

    private Utils() {

    }

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
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
