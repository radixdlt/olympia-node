package com.radix.acceptance.transaction_lookup;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.Assertions;
import com.radix.test.TestFailureException;
import com.radix.test.TransactionUtils;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.NavigationCursor;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatus;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransactionLookup extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    private static final Amount FIXED_TRANSFERAL_AMOUNT = Amount.ofMicroTokens(1);

    private Amount expectedAmount;

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
    }

    @Given("I transfer {int} XRD anywhere")
    public void i_transfer_xrd_anywhere(int amount) {
        expectedAmount = Amount.ofTokens(amount);
        txBuffer = account1.transfer(account2, expectedAmount, Optional.empty());
        logger.info("Submitted transaction {}", txBuffer);
    }

    @Then("I can lookup my transaction and observe it contains the expected information")
    public void i_can_lookup_my_transaction_and_observe_it_contains_the_expected_information() {
        var transaction = account1.lookup(txBuffer);
        Assertions.assertNativeTokenTransferTransaction(account1, account2, expectedAmount, transaction);
    }

    @Given("I perform {int} token transfers")
    public void i_perform_token_transfers(int numOfTransactions) {
        if (numOfTransactions > 5) {
            throw new TestFailureException("Too many transactions, should be < 6");
        }
        IntStream.range(0, numOfTransactions).forEach(count -> {
            var receiver = getTestAccount(count + 1);
            account1.transfer(receiver, FIXED_TRANSFERAL_AMOUNT, Optional.empty());
        });
    }

    @Then("I can observe those {int} transactions in my transaction history")
    public void i_can_observe_those_transactions_in_my_transaction_history(Integer numOfExpectedTransactions) {
        // wait until 5 transactions are visible in the history
        var history = new AtomicReference<TransactionHistory>();
        await().until(() -> {
            var historyBuffer = account1.account().history(account1.getAddress(), 100, NavigationCursor.create(""));
            if (historyBuffer.getTransactions().size() >= 6) {
                history.set(historyBuffer);
                return true;
            }
            return false;
        });

        var transactions = history.get().getTransactions().stream()
            .filter(transactionDTO -> transactionDTO.getActions().get(0).getFrom().get().equals(account1.getAddress()))
            .collect(Collectors.toList());
        assertEquals(numOfExpectedTransactions.intValue(), transactions.size());

        // This is a way to test ordering. The most recent transaction should be first in this list.
        Collections.reverse(transactions);
        IntStream.range(0, numOfExpectedTransactions).forEach(count -> {
            var transactionDTO = transactions.get(count);
            var receiver = getTestAccount(count + 1);
            Assertions.assertNativeTokenTransferTransaction(account1, receiver, FIXED_TRANSFERAL_AMOUNT, transactionDTO);
        });
    }

    @Then("I can check the status of my transaction")
    public void i_can_check_the_status_of_my_transaction() {
        var status = account1.transaction().status(txBuffer).getStatus();
        assertTrue(status.equals(TransactionStatus.PENDING) || status.equals(TransactionStatus.CONFIRMED));
    }

    @Then("It should be quickly CONFIRMED")
    public void it_should_be_quickly_confirmed() {
        TransactionUtils.waitForConfirmation(account1, txBuffer, Durations.FIVE_SECONDS);
    }

}
