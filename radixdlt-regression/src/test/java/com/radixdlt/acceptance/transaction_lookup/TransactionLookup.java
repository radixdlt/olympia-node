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

package com.radixdlt.acceptance.transaction_lookup;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.assertions.Assertions;
import com.radixdlt.client.lib.dto.TransactionHistory;
import com.radixdlt.client.lib.dto.TransactionStatus;
import com.radixdlt.test.RadixNetworkTest;
import com.radixdlt.test.utils.TestFailureException;
import com.radixdlt.test.utils.TransactionUtils;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Durations;

public class TransactionLookup extends RadixNetworkTest {

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
    Assertions.assertNativeTokenTransferTransaction(
        account1, account2, expectedAmount, transaction);
  }

  @Given("I perform {int} token transfers")
  public void i_perform_token_transfers(int numOfTransactions) {
    if (numOfTransactions > 5) {
      throw new TestFailureException("Too many transactions, should be < 6");
    }
    IntStream.range(0, numOfTransactions)
        .forEach(
            count -> {
              var receiver = getTestAccount(count + 1);
              account1.transfer(receiver, FIXED_TRANSFERAL_AMOUNT, Optional.empty());
            });
  }

  @Then("I can observe those {int} transactions in my transaction history")
  public void i_can_observe_those_transactions_in_my_transaction_history(
      Integer numOfExpectedTransactions) {
    // wait until 5 transactions are visible in the history
    var history = new AtomicReference<TransactionHistory>();
    await()
        .until(
            () -> {
              var historyBuffer =
                  account1.account().history(account1.getAddress(), 100, OptionalLong.empty());
              if (historyBuffer.getTransactions().size() >= 6) {
                history.set(historyBuffer);
                return true;
              }
              return false;
            });

    var transactions =
        history.get().getTransactions().stream()
            .filter(
                transactionDTO ->
                    transactionDTO
                        .getActions()
                        .get(0)
                        .getFrom()
                        .get()
                        .equals(account1.getAddress()))
            .collect(Collectors.toList());
    assertEquals(numOfExpectedTransactions.intValue(), transactions.size());

    // This is a way to test ordering. The most recent transaction should be first in this list.
    Collections.reverse(transactions);
    IntStream.range(0, numOfExpectedTransactions)
        .forEach(
            count -> {
              var transactionDTO = transactions.get(count);
              var receiver = getTestAccount(count + 1);
              Assertions.assertNativeTokenTransferTransaction(
                  account1, receiver, FIXED_TRANSFERAL_AMOUNT, transactionDTO);
            });
  }

  @Then("I can check the status of my transaction")
  public void i_can_check_the_status_of_my_transaction() {
    var status = account1.transaction().status(txBuffer).getStatus();
    assertTrue(
        status.equals(TransactionStatus.PENDING) || status.equals(TransactionStatus.CONFIRMED));
  }

  @Then("It should be quickly CONFIRMED")
  public void it_should_be_quickly_confirmed() {
    TransactionUtils.waitForConfirmation(account1, txBuffer, Durations.FIVE_SECONDS);
  }
}
