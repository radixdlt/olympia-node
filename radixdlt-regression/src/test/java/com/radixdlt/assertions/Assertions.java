package com.radixdlt.assertions;

import com.radixdlt.TokenCreationProperties;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.ActionType;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.Action;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.utils.TestFailureException;
import com.radixdlt.test.utils.TestingUtils;
import org.junit.Assert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Custom assertions (and wrappers for assertions) for the cucumber/acceptance tests
 */
public class Assertions {

    private Assertions() {

    }

    public static void assertNativeTokenTransferTransaction(Account account1, Account account2, Amount expectedAmount,
                                                            TransactionDTO transactionDto) {
        assertTrue(transactionDto.getMessage().isEmpty());
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

    /**
     * Will fetch token info based on the AID and compare the field values to the expected ones
     */
    public static void assertTokenProperties(Account account, AID tx, TokenCreationProperties expectedProperties) {
        var transaction = account.lookup(tx);
        var tokenInfo = TestingUtils.getTokenInfoFromTransaction(account, transaction);
        assertEquals(transaction.getEvents().get(0).getRri().get(), tokenInfo.getRri());
        assertEquals(Amount.ofTokens(expectedProperties.getAmount()).toSubunits(), tokenInfo.getCurrentSupply());
        assertEquals(expectedProperties.getSymbol(), tokenInfo.getSymbol());
        assertEquals(expectedProperties.getName(), tokenInfo.getName());
        assertEquals(expectedProperties.getDescription(), tokenInfo.getDescription());
        assertEquals(expectedProperties.getTokenInfoUrl(), tokenInfo.getTokenInfoURL());
        assertEquals(expectedProperties.getIconUrl(), tokenInfo.getIconURL());
    }

    public static void runExpectingRadixApiException(Runnable function, String message) {
        try {
            function.run();
            fail("No RadxiApiException was thrown");
        } catch (RadixApiException e) {
            assertTrue("Actual message '" + e.getMessage() + "' is not equal to expected '" + message + "'",
                e.getMessage().contains(message));
        }
    }

}
