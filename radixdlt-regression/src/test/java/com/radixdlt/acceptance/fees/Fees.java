/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.acceptance.fees;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.test.RadixNetworkTest;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    txBuffer =
        account1.fixedSupplyToken(
            "acceptancetestftoken",
            "acceptancetestftoken" + System.currentTimeMillis(),
            "description",
            "https://www.iconurl.com",
            "https://www.tokenurl.com",
            Amount.ofTokens(1000));
  }

  @When("I create a mutable supply token")
  public void i_create_a_mutable_supply_token() {
    txBuffer =
        account1.mutableSupplyToken(
            "acceptancetestmtoken",
            "acceptancetestmtoken" + System.currentTimeMillis(),
            "description",
            "https://www.iconurl.com",
            "https://www.tokenurl.com");
  }

  @When("I stake {int} XRD to a validator")
  public void i_stake_xrd_to_a_validator(int tokens) {
    var firstValidator = account1.validator().list(1, Optional.empty()).getValidators().get(0);
    var aid =
        account1.stake(firstValidator.getAddress(), Amount.ofTokens(tokens), Optional.empty());
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
    logger.debug(
        "Paid {}({})XRD in fees for token transfer with message '{}'",
        feesMinor,
        feesMajor,
        message);

    // This token transfer seems to have a baseline cost of 0.0726, so we expect to pay this plus
    // 0.0002 per char.
    // WARN: this is hardcoded for now, but when the new /health endpoint is merged then we can't
    // get fork information
    var feePerByteMicros = Amount.ofMicroTokens(200);
    var feeBaselineMicros = Amount.ofMicroTokens(72600);
    var messageCostMicros = feePerByteMicros.times(message.length());
    var totalCostMicros =
        Amount.ofSubunits(feeBaselineMicros.toSubunits().add(messageCostMicros.toSubunits()));
    assertEquals(totalCostMicros.toSubunits(), feesMinor);
  }

  @Then("I can observe that I have paid {int} extra XRD in fees")
  public void i_can_observe_that_i_have_paid_extra_xrd_fees(int extraFeeTokens) {
    var tokens = Amount.ofSubunits(account1.lookup(txBuffer).getFee());
    assertTrue(
        "Fees were less than " + extraFeeTokens + " XRD",
        tokens.toSubunits().compareTo(Amount.ofTokens(extraFeeTokens).toSubunits()) > 0);
  }
}
