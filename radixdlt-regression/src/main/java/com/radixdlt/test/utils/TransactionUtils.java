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

package com.radixdlt.test.utils;

import static org.awaitility.Awaitility.await;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.AccountAddress;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.api.ValidatorAddress;
import com.radixdlt.client.lib.api.sync.RadixApiException;
import com.radixdlt.client.lib.dto.TransactionDTO;
import com.radixdlt.client.lib.dto.TransactionStatus;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.account.Account;
import com.radixdlt.utils.UInt256;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.core.ConditionTimeoutException;

/**
 * Various helper utils that make working with our api easier. Most of the methods here use the
 * imperative API and are probably suited for simple use cases or testing.
 */
public final class TransactionUtils {

  public static final Duration DEFAULT_TX_CONFIRMATION_PATIENCE = Duration.ofMinutes(1);

  private TransactionUtils() {}

  public static TransactionRequest createTokenTransferRequest(
      AccountAddress from,
      AccountAddress to,
      String tokenRri,
      UInt256 amount,
      Optional<String> message) {
    return message
        .map(
            s ->
                TransactionRequest.createBuilder(from)
                    .transfer(from, to, amount, tokenRri)
                    .message(s)
                    .build())
        .orElseGet(
            () ->
                TransactionRequest.createBuilder(from)
                    .transfer(from, to, amount, tokenRri)
                    .build());
  }

  public static TransactionRequest createStakingRequest(
      AccountAddress from, ValidatorAddress unstakeFrom, Amount stake, Optional<String> message) {
    return message
        .map(
            s ->
                TransactionRequest.createBuilder(from)
                    .stake(from, unstakeFrom, stake.toSubunits())
                    .message(s)
                    .build())
        .orElseGet(
            () ->
                TransactionRequest.createBuilder(from)
                    .stake(from, unstakeFrom, stake.toSubunits())
                    .build());
  }

  public static TransactionRequest createUnstakingRequest(
      AccountAddress from, ValidatorAddress unstakeFrom, Amount stake, Optional<String> message) {
    return message
        .map(
            s ->
                TransactionRequest.createBuilder(from)
                    .unstake(from, unstakeFrom, stake.toSubunits())
                    .message(s)
                    .build())
        .orElseGet(
            () ->
                TransactionRequest.createBuilder(from)
                    .unstake(from, unstakeFrom, stake.toSubunits())
                    .build());
  }

  public static TransactionRequest createMintRequest(
      AccountAddress from, Amount amount, String rri, Optional<String> message) {
    return message
        .map(
            s ->
                TransactionRequest.createBuilder(from)
                    .mint(from, amount.toSubunits(), rri)
                    .message(s)
                    .build())
        .orElseGet(
            () ->
                TransactionRequest.createBuilder(from)
                    .mint(from, amount.toSubunits(), rri)
                    .build());
  }

  public static TransactionRequest createBurnRequest(
      AccountAddress from, Amount amount, String rri, Optional<String> message) {
    return message
        .map(
            s ->
                TransactionRequest.createBuilder(from)
                    .burn(from, amount.toSubunits(), rri)
                    .message(s)
                    .build())
        .orElseGet(
            () ->
                TransactionRequest.createBuilder(from)
                    .burn(from, amount.toSubunits(), rri)
                    .build());
  }

  /** Stakes tokens and waits for transaction confirmation */
  public static AID stake(
      Account account, ValidatorAddress to, Amount amount, Optional<String> message) {
    var request = createStakingRequest(account.getAddress(), to, amount, message);
    return buildFinalizeAndSubmitTransaction(account, request, true);
  }

  /** Unstakes tokens and waits for transaction confirmation */
  public static AID unstake(
      Account account, ValidatorAddress validatorAddress, Amount amount, Optional<String> message) {
    var request = createUnstakingRequest(account.getAddress(), validatorAddress, amount, message);
    return buildFinalizeAndSubmitTransaction(account, request, true);
  }

  /** Mints tokens and waits for transaction confirmation */
  public static AID mint(Account account, Amount amount, String rri, Optional<String> message) {
    var request = createMintRequest(account.getAddress(), amount, rri, message);
    return buildFinalizeAndSubmitTransaction(account, request, true);
  }

  /** Burns tokens and waits for transaction confirmation */
  public static AID burn(Account account, Amount amount, String rri, Optional<String> message) {
    var request = createBurnRequest(account.getAddress(), amount, rri, message);
    return buildFinalizeAndSubmitTransaction(account, request, true);
  }

  /** Executes an XRD transfer and waits for transaction confirmation */
  public static AID nativeTokenTransfer(
      Account sender, Account receiver, Amount amount, Optional<String> message) {
    var request =
        TransactionUtils.createTokenTransferRequest(
            sender.getAddress(),
            receiver.getAddress(),
            sender.getNativeToken().getRri(),
            amount.toSubunits(),
            message);
    return buildFinalizeAndSubmitTransaction(sender, request, true);
  }

  /** Submits a fixed supply token creation transaction and waits for its confirmation */
  public static AID createFixedSupplyToken(
      Account creator,
      String symbol,
      String name,
      String description,
      String iconUrl,
      String tokenUrl,
      Amount supply) {
    var key = creator.getKeyPair().getPublicKey();
    var request =
        TransactionRequest.createBuilder(creator.getAddress())
            .createFixed(
                creator.getAddress(),
                key,
                symbol,
                name,
                description,
                iconUrl,
                tokenUrl,
                supply.toSubunits())
            .build();
    return buildFinalizeAndSubmitTransaction(creator, request, true);
  }

  public static AID createMutableSupplyToken(
      Account creator,
      String symbol,
      String name,
      String description,
      String iconUrl,
      String tokenUrl) {
    var key = creator.getKeyPair().getPublicKey();
    var request =
        TransactionRequest.createBuilder(creator.getAddress())
            .createMutable(
                key,
                symbol,
                name,
                Optional.of(description),
                Optional.of(iconUrl),
                Optional.of(tokenUrl))
            .build();
    return buildFinalizeAndSubmitTransaction(creator, request, true);
  }

  /**
   * Builds, finalizes and submits a transaction. Can optionally wait for it to become CONFIRMED
   *
   * @return the {@link AID} of the submitted transaction
   */
  public static AID buildFinalizeAndSubmitTransaction(
      Account account, TransactionRequest request, boolean waitForConfirmation) {
    var keyPair = account.getKeyPair();
    var builtTransaction = account.transaction().build(request);
    var finalizedTransaction =
        account.transaction().finalize(builtTransaction.toFinalized(keyPair), false);
    var submittedTransaction = account.transaction().submit(finalizedTransaction);

    if (waitForConfirmation) {
      waitForConfirmation(account, submittedTransaction.getTxId());
    }

    return submittedTransaction.getTxId();
  }

  /** Will block until the a transaction for the given txID is found */
  public static TransactionDTO lookupTransaction(Account account, AID txId) {
    AtomicReference<TransactionDTO> transaction = new AtomicReference<>();
    try {
      await()
          .atMost(DEFAULT_TX_CONFIRMATION_PATIENCE)
          .ignoreException(RadixApiException.class)
          .until(
              () -> {
                transaction.set(account.transaction().lookup(txId));
                return true;
              });
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException(
          "Transaction " + txId + " was not found within " + DEFAULT_TX_CONFIRMATION_PATIENCE);
    }
    return transaction.get();
  }

  /** Will block until the given transaction is CONFIRMED */
  public static void waitForConfirmation(Account account, AID txId, Duration patience) {
    try {
      await()
          .atMost(patience)
          .until(
              () -> {
                var status = account.transaction().status(txId);
                return status.getStatus().equals(TransactionStatus.CONFIRMED);
              });
    } catch (ConditionTimeoutException e) {
      throw new TestFailureException(
          "Transaction (" + txId + ") was not CONFIRMED within " + patience);
    }
  }

  /** Will block until the given transaction is CONFIRMED. Will have the default patience */
  public static void waitForConfirmation(Account account, AID txId) {
    waitForConfirmation(account, txId, DEFAULT_TX_CONFIRMATION_PATIENCE);
  }
}
