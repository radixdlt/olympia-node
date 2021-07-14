package com.radix.acceptance.fees;

import com.radix.acceptance.AcceptanceTest;
import com.radix.test.Utils;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.utils.UInt256;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class Fees extends AcceptanceTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
    }

    @When("I transfer {int} XRD, attaching a small message to the transaction")
    public void i_transfer_xrd_attaching_a_small_message_to_the_transaction(int xrdMajor) {
        this.txBuffer = account1.transfer(account2, Amount.ofTokens(xrdMajor), Optional.of("  "));
    }

    @When("I transfer {int} XRD, attaching a large message to the transaction")
    public void i_transfer_xrd_attaching_a_large_message_to_the_transaction(int xrdMajor) {
        String largeMessage = "Z".repeat(255); // 255 chars is the max
        this.txBuffer = account1.transfer(account2, Amount.ofTokens(xrdMajor), Optional.of(largeMessage));
    }

    @Then("I can observe that I have paid fees proportional to the message bytes")
    public void i_can_observe_that_i_have_paid_fees_proportional_to_the_message_bytes() {
        TransactionDTO transaction = account1.lookup(txBuffer);
        UInt256 feesMinor = transaction.getFee();
        String feesMajor = Utils.fromMinorToMajorString(feesMinor);
        String message = transaction.getMessage().get();
        logger.debug("Paid {}({})XRD in fees for token transfer with message '{}'", feesMinor, feesMajor, message);

        // This token transfer seems to have a baseline cost of 0.0726, so we expect to pay this plus 0.0002 per char.
        // TODO the above should not be hardcoded, but extracted from the fork config. Fees can vary between forks.
        Amount feePerByteMicros = Amount.ofMicroTokens(200);
        Amount feeBaselineMicros = Amount.ofMicroTokens(72600);
        Amount messageCostMicros = feePerByteMicros.times(message.length());
        Amount totalCostMicros = Amount.ofSubunits(feeBaselineMicros.toSubunits().add(messageCostMicros.toSubunits()));
        assertEquals(totalCostMicros.toSubunits(), feesMinor);
    }

}
