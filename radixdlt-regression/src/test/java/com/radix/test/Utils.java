package com.radix.test;

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
     * Will wait until the native token balance increases by any maount
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

    public static UInt256 fromMinorToMajor(UInt256 minorAmount) {
        return minorAmount.divide(TokenUtils.SUB_UNITS);
    }

    public static <R> R toTestFailureException(Failure failure) {
        throw new TestFailureException(failure.message());
    }

    public static UInt256 fromMajorToMinor(int amountMajor) {
        return UInt256.from(amountMajor).multiply(TokenUtils.SUB_UNITS);
    }

    public static UInt256 fromMajorToMinor(long amountMajor) {
        return UInt256.from(amountMajor).multiply(TokenUtils.SUB_UNITS);
    }

    public static UInt256 fromMajorToMinor(UInt256 amountMajor) {
        return amountMajor.multiply(TokenUtils.SUB_UNITS);
    }

    //TODO: fix it. the method requires addressing or network ID as a parameter
    public static ValidatorAddress createValidatorAddress(ValidatorDTO validatorDTO) {
//        try {
//            //return ValidatorAddress.create(validatorDTO.getAddress());
//
//        } catch (DeserializeException e) {
            throw new IllegalStateException("Failed to parse validator address: " + validatorDTO.getAddress());
//        }
    }
}
