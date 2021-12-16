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

package com.radixdlt.acceptance.token_transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.RadixNetworkTest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenTransfer extends RadixNetworkTest {

  private static final Logger logger = LogManager.getLogger();

  private Amount amountBuffer;

  @Given("I have two accounts with funds at a suitable Radix network")
  public void i_have_two_accounts_with_funds_at_a_suitable_radix_network() {
    faucet(account1);
    faucet(account2);
  }

  @Given("I have an account with {int} XRD at a suitable Radix network")
  public void i_have_an_account_with_xrd_at_a_suitable_radix_network(int tokens) {
    faucet(account1, Amount.ofTokens(tokens));
  }

  @When("I transfer {int} XRD to myself")
  public void i_transfer_xrd_to_myself(int tokensToTransfer) {
    this.amountBuffer = Amount.ofSubunits(account1.getOwnNativeTokenBalance().getAmount());
    this.txBuffer =
        account1.transfer(account1, Amount.ofTokens(tokensToTransfer), Optional.empty());
  }

  @And("I transfer {int} XRD from the first account to the second")
  public void i_transfer_xrd_from_the_first_account_to_the_second(int amount) {
    var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
    var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
    var transferredAmount = Amount.ofTokens(amount);

    var transferTxId = account1.transfer(account2, transferredAmount, Optional.of("hello!"));
    var fee = account1.lookup(transferTxId).getFee();

    assertEquals(
        account1BalanceBefore.subtract(transferredAmount.toSubunits()).subtract(fee),
        account1.getOwnNativeTokenBalance().getAmount());
    assertEquals(
        account2BalanceBefore.add(transferredAmount.toSubunits()),
        account2.getOwnNativeTokenBalance().getAmount());
  }

  @Then("I have the same amount of tokens, minus fees")
  public void i_have_the_same_amount_of_tokens_minus_fees() {
    var amount = Amount.ofSubunits(account1.getOwnNativeTokenBalance().getAmount());
    var expectedAmount = amountBuffer.toSubunits().subtract(account1.lookup(txBuffer).getFee());
    assertEquals(expectedAmount, amount.toSubunits());
  }

  @Then("the second account can transfer {int} XRD back to the first")
  public void that_account_can_transfer_xrd_back_to_me(int amount) {
    var account1BalanceBefore = account1.getOwnNativeTokenBalance().getAmount();
    var account2BalanceBefore = account2.getOwnNativeTokenBalance().getAmount();
    var transferredAmount = Amount.ofTokens(amount);

    var transferTxId = account2.transfer(account1, transferredAmount, Optional.of("hello back!"));
    var fee = account2.lookup(transferTxId).getFee();

    assertEquals(
        account2BalanceBefore.subtract(transferredAmount.toSubunits()).subtract(fee),
        account2.getOwnNativeTokenBalance().getAmount());
    assertEquals(
        account1BalanceBefore.add(transferredAmount.toSubunits()),
        account1.getOwnNativeTokenBalance().getAmount());
  }

  @Then("I cannot transfer {int} XRD to another account")
  public void i_cannot_transfer_xrd_to_another_account(int tokensToTransfer) {
    boolean hasTransferred;
    try {
      account1.transfer(account2, Amount.ofTokens(tokensToTransfer), Optional.empty());
      hasTransferred = true;
    } catch (RadixApiException e) {
      hasTransferred = false;
    }
    assertFalse(hasTransferred);
  }
}
