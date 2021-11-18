package com.radixdlt.acceptance.fees;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.test.RadixNetworkTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Fees extends RadixNetworkTest {

    private static final Logger logger = LogManager.getLogger();

    @Given("I have an account with funds at a suitable Radix network")
    public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
        faucet(account1);
    }

    @Given("I have an account with {int} XRD at a suitable Radix network")
    public void i_have_an_account_with_xrd_at_a_suitable_radix_network(int tokens) {
        faucet(account1, Amount.ofTokens(tokens));
    }

    @When("I transfer {int} XRD, attaching a small message to the transaction")
    public void i_transfer_xrd_attaching_a_small_message_to_the_transaction(int tokens) {
        this.txBuffer = account1.transfer(account2, Amount.ofTokens(tokens), Optional.of("  "));
    }

    @When("I transfer {int} XRD, attaching a large message to the transaction")
    public void i_transfer_xrd_attaching_a_large_message_to_the_transaction(int tokens) {
        var largeMessage = "Z".repeat(255); // 255 chars is the max
        txBuffer = account1.transfer(account2, Amount.ofTokens(tokens), Optional.of(largeMessage));
    }

    @When("I create a fixed supply token")
    public void i_create_a_fixed_supply_token() {
        txBuffer = account1.fixedSupplyToken("acceptancetestftoken", "acceptancetestftoken" + System.currentTimeMillis(),
            "description", "https://www.iconurl.com", "https://www.tokenurl.com", Amount.ofTokens(1000));
    }

    @When("I create a mutable supply token")
    public void i_create_a_mutable_supply_token() {
        txBuffer = account1.mutableSupplyToken("acceptancetestmtoken", "acceptancetestmtoken" + System.currentTimeMillis(),
            "description", "https://www.iconurl.com", "https://www.tokenurl.com");
    }

    @When("I stake {int} XRD to a validator")
    public void i_stake_xrd_to_a_validator(int tokens) {
        var firstValidator = account1.validator().list(1, Optional.empty()).getValidators().get(0);
        var aid = account1.stake(firstValidator.getAddress(), Amount.ofTokens(tokens), Optional.empty());
        var transaction = account1.lookup(aid);
        logger.info(transaction);
        logger.info(transaction.getFee());
        this.txBuffer = transaction.getTxID();
    }

    @Then("I can observe that I have paid fees proportional to the message bytes")
    public void i_can_observe_that_i_have_paid_fees_proportional_to_the_message_bytes() {
        var transaction = account1.lookup(txBuffer);
        var feesMinor = transaction.getFee();
        var feesMajor = Amount.ofSubunits(feesMinor);
        var message = transaction.getMessage().get();
        logger.debug("Paid {}({})XRD in fees for token transfer with message '{}'", feesMinor, feesMajor, message);

        // This token transfer seems to have a baseline cost of 0.0726, so we expect to pay this plus 0.0002 per char.
        // WARN: this is hardcoded for now, but when the new /health endpoint is merged then we can't get fork information
        var feePerByteMicros = Amount.ofMicroTokens(200);
        var feeBaselineMicros = Amount.ofMicroTokens(72600);
        var messageCostMicros = feePerByteMicros.times(message.length());
        var totalCostMicros = Amount.ofSubunits(feeBaselineMicros.toSubunits().add(messageCostMicros.toSubunits()));
        assertEquals(totalCostMicros.toSubunits(), feesMinor);
    }

    @Then("I can observe that I have paid {int} extra XRD in fees")
    public void i_can_observe_that_i_have_paid_extra_xrd_fees(int extraFeeTokens) {
        var tokens = Amount.ofSubunits(account1.lookup(txBuffer).getFee());
        assertTrue("Fees were less than " + extraFeeTokens + " XRD",
            tokens.toSubunits().compareTo(Amount.ofTokens(extraFeeTokens).toSubunits()) > 0);
    }

}
