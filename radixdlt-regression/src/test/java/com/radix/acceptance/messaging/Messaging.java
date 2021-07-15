package com.radix.acceptance.messaging;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.TestFailureException;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.TransactionDTO;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.junit.Assert.*;

public class Messaging extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private String expectedMessage;

    @Given("I have two accounts with funds at a suitable Radix network")
    public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
        faucet(account2);
    }

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
    }

    @Given("I send the plaintext message {string} from the first account to the second")
    public void i_send_the_plaintext_message_from_the_first_account_to_the_second(String message) {
        txBuffer = account1.transfer(account2, Amount.ofTokens(1), Optional.of(message));
        expectedMessage = message;
        logger.info("Submitted token transfer ({}) with message '{}'", txBuffer.toString(), message);
    }

    @Then("my message can be read")
    public void my_message_can_be_read() {
        TransactionDTO transactionWithMessage = account1.lookup(txBuffer);
        assertEquals(expectedMessage, transactionWithMessage.getMessage().orElseThrow(() ->
            new TestFailureException("No message found in transaction")));
    }

    @Given("I send a plaintext message with more than {int} characters")
    public void i_send_a_plaintext_message_with_more_than_characters(Integer characters) {
        String message = "!".repeat(characters + 1);
        try {
            account1.transfer(account2, Amount.ofTokens(1), Optional.of(message));
        } catch (RadixApiException e) {
            assertTrue(e.getMessage().contains("Data length is 256 but must be <= 255"));
            txBuffer = null;
        }
    }

    @Then("the transaction will not be submitted")
    public void the_transaction_will_not_be_submitted() {
        assertNull(txBuffer);
    }

}
