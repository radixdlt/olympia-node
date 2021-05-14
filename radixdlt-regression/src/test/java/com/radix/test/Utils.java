package com.radix.test;

import com.radix.test.account.Account;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Failure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public final class Utils {

    private static final Logger logger = LogManager.getLogger();

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static void waitForBalance(Account account, int amount) {
        waitForBalance(account, UInt256.from(amount));
    }

    public static void waitForBalance(Account account, UInt256 amount) {
        try {
            await().atMost(Durations.ONE_MINUTE).until(() -> account.getOwnNativeTokenBalance().getAmount()ownTokenBalances().map(tokenBalancesDTO -> {
                if (tokenBalancesDTO.getTokenBalances().size() == 0) {
                    return false;
                }
                
                UInt256 balanceForToken = Utils.getBalanceForToken(tokenBalancesDTO, account.getNativeToken());


                return (balanceForToken != null) && (balanceForToken.compareTo(amount) >= 0);
            }).fold(failure -> false, mapper -> mapper));
        } catch (ConditionTimeoutException e) {
            throw new RuntimeException("Account's balance did not increase");
        }
    }

    /**
     * Returns the balance for the given token rri, or null if not found.
     */
    public static UInt256 getBalanceForToken(TokenBalancesDTO tokenBalancesDTO, TokenInfoDTO tokenRri) {
        var balances = tokenBalancesDTO.getTokenBalances().stream().filter(balanceDTO ->
                balanceDTO.getRri().equals(tokenRri.getRri())).collect(Collectors.toList());
        return balances.isEmpty() ? null : toMajor(balances.get(0).getAmount());
    }

    public static UInt256 toMajor(long minorAmount) {
        return UInt256.from(minorAmount).divide(TokenDefinitionUtils.SUB_UNITS);
    }

    public static UInt256 toMajor(UInt256 minorAmount) {
        return minorAmount.divide(TokenDefinitionUtils.SUB_UNITS);
    }

    public static <R> R toRuntimeException(Failure failure) {
        throw new RuntimeException(failure.message());
    }
}
