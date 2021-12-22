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

package com.radixdlt.assertions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

/** Custom assertions (and wrappers for assertions) for the cucumber/acceptance tests */
public class Assertions {

  private Assertions() {}

  public static void assertNativeTokenTransferTransaction(
      Account account1, Account account2, Amount expectedAmount, TransactionDTO transactionDto) {
    assertTrue(transactionDto.getMessage().isEmpty());
    Assert.assertEquals(1, transactionDto.getActions().size());
    Action singleAction = transactionDto.getActions().get(0);
    Assert.assertEquals(
        expectedAmount.toSubunits(),
        singleAction
            .getAmount()
            .orElseThrow(() -> new TestFailureException("no amount in transaction")));
    Assert.assertEquals(
        account1.getAddress(),
        singleAction
            .getFrom()
            .orElseThrow(() -> new TestFailureException("no sender in transaction")));
    Assert.assertEquals(
        account2.getAddress(),
        singleAction
            .getTo()
            .orElseThrow(() -> new TestFailureException("no receiver in transaction")));
    Assert.assertEquals(ActionType.TRANSFER, singleAction.getType());
  }

  /** Will fetch token info based on the AID and compare the field values to the expected ones */
  public static void assertTokenProperties(
      Account account, AID tx, TokenCreationProperties expectedProperties) {
    var transaction = account.lookup(tx);
    var tokenInfo = TestingUtils.getTokenInfoFromTransaction(account, transaction);
    assertEquals(transaction.getEvents().get(0).getRri().get(), tokenInfo.getRri());
    assertEquals(
        Amount.ofTokens(expectedProperties.getAmount()).toSubunits(), tokenInfo.getCurrentSupply());
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
      assertTrue(
          "Actual message '" + e.getMessage() + "' is not equal to expected '" + message + "'",
          e.getMessage().contains(message));
    }
  }
}
