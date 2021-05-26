package com.radix.acceptance.messaging;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.TestFailureException;
import com.radix.test.TransactionUtils;
import com.radix.test.Utils;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TxDTO;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.junit.Assert.assertEquals;

public class Messaging extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private TxDTO txWithMessage;
    private String expectedMessage;

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        callFaucetAndWaitForTokens(account1);
        callFaucetAndWaitForTokens(account2);
    }

    @Given("I send the plaintext message {string} from the first account to the second")
    public void i_send_the_plaintext_message_from_the_first_account_to_the_second(String message) {
        txWithMessage = TransactionUtils.performNativeTokenTransfer(account1, account2, 2, message)
            .fold(Utils::toTestFailureException, txDTO -> txDTO);
        Utils.waitForBalanceToIncrease(account2);
        expectedMessage = message;
        logger.info("Submitted token transfer ({}) with message '{}'", txWithMessage.getTxId(), message);
    }

    @Then("my message can be read")
    public void my_message_can_be_read() {
        var txWithMessage = getTestAccount(1).lookupTransaction(this.txWithMessage.getTxId())
            .fold(Utils::toTestFailureException, transactionDTO -> transactionDTO);
        assertEquals(expectedMessage, txWithMessage.getMessage().orElseThrow(() ->
            new TestFailureException("No message found in transaction")));
    }

}
