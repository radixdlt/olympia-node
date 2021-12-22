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

package com.radixdlt.test.system;

import com.radixdlt.application.tokens.Amount;
import com.radixdlt.client.lib.api.TransactionRequest;
import com.radixdlt.client.lib.dto.Event;
import com.radixdlt.client.lib.dto.ValidatorDTO;
import com.radixdlt.identifiers.AID;
import com.radixdlt.test.account.Account;
import com.radixdlt.test.crypto.BitcoinJBIP32Path;
import com.radixdlt.test.crypto.DefaultHDKeyPairDerivation;
import com.radixdlt.test.crypto.errors.HDPathException;
import com.radixdlt.test.crypto.errors.MnemonicException;
import com.radixdlt.test.network.RadixNetworkConfiguration;
import com.radixdlt.test.system.harness.SystemTest;
import com.radixdlt.test.utils.TestFailureException;
import com.radixdlt.test.utils.TestingUtils;
import com.radixdlt.test.utils.TransactionUtils;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.util.StringUtils;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionsTests extends SystemTest {

  protected static final Logger logger = LogManager.getLogger();

  private static final String BIP32_PATH = "m/44'/1022'/0'/0/0'";
  private static final Amount AMOUNT_TO_STAKE = Amount.ofTokens(100);
  private static final Amount TOKENS_TO_MINT = Amount.ofTokens(250000);

  // in case there is no seed phrase, we use a known address for this test
  private static final int HARDCODED_ADDRESS_PRIVATE_KEY = 5;

  private final Account account;
  private final String version;
  private final ValidatorDTO firstValidator;
  private String mutableTokenRri;

  TransactionsTests() {
    // env properties specific to this test:
    var seedPhrase = TestingUtils.getEnvWithDefault("RADIXDLT_TESTING_ACCOUNT_SEED_PHRASE", "");
    // the node version of the first node in the network. This will be saved in the transaction
    // message and will tell us
    // which version this transaction came from
    this.version = radixNetwork.getVersionOfFirstNode();

    var configuration = radixNetwork.getConfiguration();
    this.account =
        StringUtils.isNotBlank(seedPhrase)
            ? initializeAccountFromSeedPhrase(configuration, seedPhrase)
            : Account.initialize(
                configuration.getJsonRpcRootUrl(),
                configuration.getPrimaryPort(),
                configuration.getSecondaryPort(),
                TestingUtils.createKeyPairFromNumber(HARDCODED_ADDRESS_PRIVATE_KEY),
                configuration.getBasicAuth());
    logger.info(
        "Using address {} with balances: {}",
        account.getAddressForNetwork(),
        account.getOwnTokenBalances().getTokenBalances());
    firstValidator = account.validator().list(100, Optional.empty()).getValidators().get(0);
  }

  @BeforeEach
  void setup() {
    callFaucetIfNeeded(account);
  }

  @Test
  void token_transfer() {
    var txId = account.transfer(account1, Amount.ofMicroTokens(2500), createTestMessageOptional());
    logger.info("Token transfer txId: {}", txId);
  }

  @Test
  @Order(1)
  void stake() {
    callFaucetIfNeeded(account, AMOUNT_TO_STAKE);
    logger.info("Staking {} to validator {}...", AMOUNT_TO_STAKE, firstValidator);
    var txId =
        account.stake(firstValidator.getAddress(), AMOUNT_TO_STAKE, createTestMessageOptional());
    logger.info("Stake tokens txId: {}", txId);
  }

  @Test
  @Order(2)
  void multi_action_transaction() {
    callFaucetIfNeeded(account, AMOUNT_TO_STAKE.times(3)); // fees are high
    var nativeTokenRri = account.getNativeToken().getRri();
    var publicKey = account.getKeyPair().getPublicKey();
    var timestamp = System.currentTimeMillis();
    var request =
        TransactionRequest.createBuilder(account.getAddress())
            .stake(account.getAddress(), firstValidator.getAddress(), AMOUNT_TO_STAKE.toSubunits())
            .transfer(
                account.getAddress(),
                account1.getAddress(),
                Amount.ofMicroTokens(1).toSubunits(),
                nativeTokenRri)
            .transfer(
                account.getAddress(),
                account2.getAddress(),
                Amount.ofMicroTokens(2).toSubunits(),
                nativeTokenRri)
            .createMutable(
                publicKey,
                "mtt2" + timestamp,
                "mtesttoken2" + timestamp,
                Optional.empty(),
                Optional.empty(),
                Optional.empty())
            .createFixed(
                account.getAddress(),
                publicKey,
                "ftt2" + timestamp,
                "ftesttoken2",
                "description",
                "https://www.icon.com",
                "https://www.token.com",
                Amount.ofTokens(1000000).toSubunits())
            .build();
    logger.info("Submitting a multi-action transaction...");
    var txId = TransactionUtils.buildFinalizeAndSubmitTransaction(account, request, true);
    logger.info("Multi-action txId: {}", txId);
    logger.info("Waiting until end of current epoch...");
    TestingUtils.waitUntilEndNextEpoch(account);
  }

  @Test
  @Order(3)
  void unstake() {
    var totalStake = AMOUNT_TO_STAKE.times(2);
    logger.info("Unstaking {} from validator {}...", totalStake, firstValidator);
    var txId =
        account.unstake(firstValidator.getAddress(), AMOUNT_TO_STAKE.times(2), Optional.empty());
    logger.info("Unstake tokens txId: {}", txId);
  }

  @Test
  @Order(4)
  void mutable_supply_token_creation() {
    callFaucetIfNeeded(account, Amount.ofTokens(100));
    var timestamp = System.currentTimeMillis();
    var txId =
        account.mutableSupplyToken(
            "mtt" + timestamp,
            "mtesttoken" + timestamp,
            "A mutable supply token created for testing. Version " + version,
            "https://www.mtesttoken.ico",
            "https://www.mtesttoken.com");
    logger.info("Mutable supply token txId: {}", txId);
    setMutableTokenRri(txId);
    logger.info("Mutable supply token's rri: {}", mutableTokenRri);
  }

  @Test
  @Order(5)
  void mint_mutable_supply_token() {
    var txId = account.mint(TOKENS_TO_MINT, mutableTokenRri, createTestMessageOptional());
    logger.info("Token {} mint txId: {}", mutableTokenRri, txId);
  }

  @Test
  @Order(6)
  void burn_mutable_supply_token() {
    var txId = account.burn(TOKENS_TO_MINT, mutableTokenRri, createTestMessageOptional());
    logger.info("Token {} burn txId: {}", mutableTokenRri, txId);
  }

  @Test
  void fixed_supply_token_creation() {
    callFaucetIfNeeded(account, Amount.ofTokens(100));
    var timestamp = System.currentTimeMillis();
    var supply = Amount.ofTokens(1000000);
    var txId =
        account.fixedSupplyToken(
            "ftt" + timestamp,
            "ftesttoken_" + timestamp,
            "A fixed supply (" + supply + ") token created for testing. Version " + version,
            "https://www.ftesttoken.ico",
            "https://www.ftesttoken.com",
            supply);
    logger.info("Fixed supply token txId: {}", txId);
  }

  private Account initializeAccountFromSeedPhrase(
      RadixNetworkConfiguration configuration, String seedPhrase) {
    logger.info("Will generate keypair from seed phrase...");
    try {
      var keyPairDerivation = DefaultHDKeyPairDerivation.fromMnemonicString(seedPhrase);
      var bip32path = BitcoinJBIP32Path.fromString(BIP32_PATH);
      var keyPair = keyPairDerivation.deriveKeyAtPath(bip32path).keyPair();
      return Account.initialize(
          configuration.getJsonRpcRootUrl(),
          configuration.getPrimaryPort(),
          configuration.getSecondaryPort(),
          keyPair,
          configuration.getBasicAuth());
    } catch (MnemonicException | HDPathException e) {
      throw new RuntimeException("Could not generate keypair from seed phrase", e);
    }
  }

  private void setMutableTokenRri(AID txId) {
    var events = account.transaction().lookup(txId).getEvents();
    if (events.size() != 1) {
      throw new TestFailureException("Expected only 1 token_created action, got " + events);
    }
    Event event = events.get(0);
    event
        .getRri()
        .map(rri -> this.mutableTokenRri = rri)
        .orElseThrow(() -> new TestFailureException("No rri found in event"));
  }

  private Optional<String> createTestMessageOptional() {
    return Optional.of("Autogenerated test transaction from version '" + version + "'");
  }

  private void callFaucetIfNeeded(Account account, Amount amount) {
    if (account.getOwnNativeTokenBalance().getAmount().compareTo(amount.toSubunits()) < 1) {
      faucet(account, amount);
    }
  }

  private void callFaucetIfNeeded(Account account) {
    if (account.getOwnNativeTokenBalance().getAmount().compareTo(Amount.ofTokens(10).toSubunits())
        < 1) {
      faucet(account);
    }
  }
}
