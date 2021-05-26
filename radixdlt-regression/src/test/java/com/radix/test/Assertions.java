package com.radix.test;

import com.radix.test.account.Account;
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.client.lib.dto.ActionDTO;
import com.radixdlt.client.lib.dto.TransactionDTO;
import io.cucumber.java.bs.A;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Custom assertions (and wrappers for assertions) for the cucumber/acceptance tests
 */
public class Assertions {

    public Assertions() {

    }

    public static void assertNativeTokenTransferTransaction(Account account1, Account account2, int expectAmountMajor,
                                                            TransactionDTO transactionDto) {
        assertTrue(transactionDto.getMessage().isEmpty());
        assertEquals(1, transactionDto.getActions().size());
        ActionDTO singleAction = transactionDto.getActions().get(0);
        assertEquals(Utils.fromMajorToMinor(expectAmountMajor),
            singleAction.getAmount().orElseThrow(() -> new TestFailureException("no amount in transaction")));
        assertEquals(account1.getAddress(),
            singleAction.getFrom().orElseThrow(() -> new TestFailureException("no sender in transaction")));
        assertEquals(account2.getAddress(),
            singleAction.getTo().orElseThrow(() -> new TestFailureException("no receiver in transaction")));
        assertEquals(ActionType.TRANSFER, singleAction.getType());
    }

}
