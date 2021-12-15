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

package com.radixdlt.acceptance.fixed_supply_tokens;

import static org.junit.Assert.assertEquals;

import com.radixdlt.TokenCreationProperties;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.utils.TestingUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;

public class FixedSupplyTokens extends RadixNetworkTest {

  private TokenCreationProperties tokenProperties;

  @Given("I have an account with funds at a suitable Radix network")
  public void i_have_an_account_with_funds_at_a_suitable_radix_network() {
    faucet(account1, Amount.ofTokens(110));
  }

  @When(
      "I create a fixed supply token with properties: {string}, {string}, {string}, {string},"
          + " {string}, {int} total supply")
  public void i_create_a_fixed_supply_token_with_properties(
      String symbol,
      String name,
      String description,
      String iconUrl,
      String tokenInfoUrl,
      int amount) {
    this.tokenProperties =
        new TokenCreationProperties(symbol, name, description, iconUrl, tokenInfoUrl, amount);
    txBuffer =
        account1.fixedSupplyToken(
            symbol, name, description, iconUrl, tokenInfoUrl, Amount.ofTokens(amount));
  }

  @When("I create a fixed supply token with a total supply of {int}")
  public void i_create_a_fixed_supply_token_with_a_supply_of(int totalSupplyTokens) {
    var symbol = "fsymbol";
    var name = "fname";
    var description = "acceptance test token";
    var iconUrl = "http://icon.com";
    var tokenInfoUrl = "http://token.com";
    this.tokenProperties =
        new TokenCreationProperties(
            symbol, name, description, iconUrl, tokenInfoUrl, totalSupplyTokens);
    txBuffer =
        account1.fixedSupplyToken(
            symbol, name, description, iconUrl, tokenInfoUrl, Amount.ofTokens(totalSupplyTokens));
  }

  @Then("I can observe that the token has been created, with the correct values")
  public void i_can_observe_that_the_token_has_been_created_with_the_correct_values() {
    Assertions.assertTokenProperties(account1, txBuffer, tokenProperties);
  }

  @Then("I can send {int} of my new tokens to another account")
  public void i_can_send_of_my_new_tokens_to_another_account(int tokensToTransfer) {
    var rri = TestingUtils.getRriFromAID(account1, txBuffer);
    account1.transfer(account2, Amount.ofTokens(tokensToTransfer), rri, Optional.empty());
  }

  @Then("I can observe that the token has been created, with a total supply of {int}")
  public void i_can_observe_that_the_token_has_been_created_with_a_total_supply_of(
      int totalSupplyTokens) {
    var tokenInfo = TestingUtils.getTokenInfoFromAID(account1, txBuffer);
    assertEquals(Amount.ofTokens(totalSupplyTokens).toSubunits(), tokenInfo.getCurrentSupply());
  }

  @Then("I cannot transfer more than the total supply")
  public void i_cannot_transfer_more_than_the_total_supply() {
    var rri = TestingUtils.getRriFromAID(account1, txBuffer);
    var totalSupplyPlusOneToken = Amount.ofTokens(tokenProperties.getAmount() + 1);
    try {
      account1.transfer(account2, totalSupplyPlusOneToken, rri, Optional.empty());
    } catch (RadixApiException e) {
      // sadly we don't get a more specific exception
      assertEquals(
          "Transaction submission failed: Not enough balance for transfer.", e.getMessage());
    }
  }
}
