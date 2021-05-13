package com.radix.test;

import com.radix.test.account.Account;
import com.radixdlt.client.lib.dto.TokenBalancesDTO;
import com.radixdlt.client.lib.dto.TokenInfoDTO;
import com.radixdlt.utils.functional.Result;
import org.awaitility.Durations;
import org.awaitility.core.ConditionTimeoutException;

import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;

public final class Utils {

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static void waitForBalance(Result<Account> testAccount, long amount) {
        testAccount.onSuccess(account -> {
            try {
                await().atMost(Durations.ONE_MINUTE).until(() -> account.ownTokenBalances().map(tokenBalancesDTO -> {
                    if (tokenBalancesDTO.getTokenBalances().size() == 0) {
                        return false;
                    }
                    Long nativeBalance = Utils.getBalanceForToken(tokenBalancesDTO, account.getNativeToken());
                    return nativeBalance != null && nativeBalance >= amount;
                }).fold(failure -> false, mapper -> mapper));
            } catch (ConditionTimeoutException e) {
                throw new RuntimeException("Account's balance did not increase");
            }
        });
    }

    /**
     * Returns the balance for the given token rri, or null if not found. A long should be enough to cover all
     * the UINT256 values
     */
    public static Long getBalanceForToken(TokenBalancesDTO tokenBalancesDTO, TokenInfoDTO tokenRri) {
        var balances = tokenBalancesDTO.getTokenBalances().stream().filter(balanceDTO ->
                balanceDTO.getRri().equals(tokenRri.getRri())).collect(Collectors.toList());
        String nativeAmountString = balances.get(0).getAmount().toString();
        if (!nativeAmountString.isBlank()) {
            nativeAmountString = nativeAmountString.substring(0, nativeAmountString.length() - 18);
        }
        return balances.isEmpty() ? null : Long.valueOf(nativeAmountString);
    }

}
