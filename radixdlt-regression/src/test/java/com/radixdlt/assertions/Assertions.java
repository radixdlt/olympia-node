package com.radixdlt.assertions;

import org.junit.Assert;

import com.radixdlt.api.dto.common.Action;
import com.radixdlt.api.dto.common.ActionType;
import com.radixdlt.api.dto.response.TransactionDTO;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.utils.TestFailureException;

/**
 * Custom assertions (and wrappers for assertions) for the cucumber/acceptance tests
 */
public class Assertions {

    private Assertions() {

    }

    public static void assertNativeTokenTransferTransaction(Account account1, Account account2, Amount expectedAmount,
                                                            TransactionDTO transactionDto) {
        Assert.assertTrue(transactionDto.getMessage().isEmpty());
        Assert.assertEquals(1, transactionDto.getActions().size());
        Action singleAction = transactionDto.getActions().get(0);
        Assert.assertEquals(expectedAmount.toSubunits(),
            singleAction.getAmount().orElseThrow(() -> new TestFailureException("no amount in transaction")));
        Assert.assertEquals(account1.getAddress(),
            singleAction.getFrom().orElseThrow(() -> new TestFailureException("no sender in transaction")));
        Assert.assertEquals(account2.getAddress(),
            singleAction.getTo().orElseThrow(() -> new TestFailureException("no receiver in transaction")));
        Assert.assertEquals(ActionType.TRANSFER, singleAction.getType());
    }
}
