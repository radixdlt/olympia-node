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

package com.radixdlt.api.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.radixdlt.api.ApiTest;
import com.radixdlt.api.core.handlers.ConstructionBuildHandler;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.BelowMinimumStakeError;
import com.radixdlt.api.core.openapitools.model.ConstructionBuildRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionBuildResponse;
import com.radixdlt.api.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.NotEnoughResourcesError;
import com.radixdlt.api.core.openapitools.model.NotValidatorOwnerError;
import com.radixdlt.api.core.openapitools.model.Operation;
import com.radixdlt.api.core.openapitools.model.OperationGroup;
import com.radixdlt.api.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.openapitools.model.ResourceDepositOperationNotSupportedByEntityError;
import com.radixdlt.api.core.openapitools.model.ResourceIdentifier;
import com.radixdlt.api.core.openapitools.model.ResourceWithdrawOperationNotSupportedByEntityError;
import com.radixdlt.api.core.openapitools.model.SubEntity;
import com.radixdlt.api.core.openapitools.model.UnexpectedError;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.PrivateKeys;
import com.radixdlt.utils.UInt128;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

public final class ConstructionBuildTransferStakeUnstakeTest extends ApiTest {
  @Inject private ConstructionBuildHandler sut;
  @Inject private CoreModelMapper coreModelMapper;
  @Inject private Forks forks;
  @Inject @Self private ECPublicKey self;
  @Inject private RadixEngine<LedgerAndBFTProof> radixEngine;

  private ConstructionBuildRequest buildTransfer(
      ResourceIdentifier resourceIdentifier,
      UInt256 amount,
      EntityIdentifier from,
      EntityIdentifier to) {
    var accountAddress = REAddr.ofPubKeyAccount(self);
    return new ConstructionBuildRequest()
        .networkIdentifier(new NetworkIdentifier().network("localnet"))
        .feePayer(coreModelMapper.entityIdentifier(accountAddress))
        .addOperationGroupsItem(
            new OperationGroup()
                .addOperationsItem(
                    new Operation()
                        .entityIdentifier(from)
                        .amount(
                            new ResourceAmount()
                                .resourceIdentifier(resourceIdentifier)
                                .value("-" + amount.toString())))
                .addOperationsItem(
                    new Operation()
                        .entityIdentifier(to)
                        .amount(
                            new ResourceAmount()
                                .resourceIdentifier(resourceIdentifier)
                                .value(amount.toString()))));
  }

  @Test
  public void transferring_tokens_should_work() throws Exception {
    // Arrange
    start();

    // Act
    var otherAddress = REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(2).getPublicKey());
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            UInt256.ONE,
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifier(otherAddress));
    var response = handleRequestWithExpectedResponse(sut, request, ConstructionBuildResponse.class);

    // Assert
    assertThat(Bytes.fromHexString(response.getPayloadToSign())).isNotNull();
    assertThat(Bytes.fromHexString(response.getUnsignedTransaction())).isNotNull();
  }

  @Test
  public void setting_disable_mint_and_burn_should_set_correct_header() throws Exception {
    // Arrange
    start();

    // Act
    var otherAddress = REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(2).getPublicKey());
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            UInt256.ONE,
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifier(otherAddress));
    request.setDisableResourceAllocateAndDestroy(true);
    var response = handleRequestWithExpectedResponse(sut, request, ConstructionBuildResponse.class);

    // Assert
    var bytes = Bytes.fromHexString(response.getUnsignedTransaction());
    assertThat(bytes).isNotNull();
    assertThat(Bytes.fromHexString(response.getPayloadToSign())).isNotNull();
    var parsed = radixEngine.getParser().parse(Txn.create(bytes));
    assertThat(parsed.disableResourceAllocAndDestroy()).isTrue();
  }

  @Test
  public void staking_tokens_directly_to_validator_should_fail() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            getLiquidAmount().toSubunits().subtract(Amount.ofTokens(1).toSubunits()),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifier(self));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails())
        .isInstanceOf(ResourceDepositOperationNotSupportedByEntityError.class);
  }

  @Test
  public void transferring_too_many_tokens_should_fail() throws Exception {
    // Arrange
    start();

    // Act
    var otherAddress = REAddr.ofPubKeyAccount(PrivateKeys.ofNumeric(2).getPublicKey());
    var tooLargeAmount = getLiquidAmount().toSubunits().add(UInt256.ONE);
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            tooLargeAmount,
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifier(otherAddress));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails()).isInstanceOf(NotEnoughResourcesError.class);
  }

  @Test
  public void staking_tokens_to_self_should_succeed() throws Exception {
    // Arrange
    start();

    // Act
    var selfAddress = REAddr.ofPubKeyAccount(self);
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            getLiquidAmount().toSubunits().subtract(Amount.ofTokens(1).toSubunits()),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifierPreparedStake(selfAddress, self));
    var response = handleRequestWithExpectedResponse(sut, request, ConstructionBuildResponse.class);

    // Assert
    assertThat(Bytes.fromHexString(response.getPayloadToSign())).isNotNull();
    assertThat(Bytes.fromHexString(response.getUnsignedTransaction())).isNotNull();
  }

  @Test
  public void staking_too_little_tokens_should_fail() throws Exception {
    // Arrange
    start();

    // Act
    var selfAddress = REAddr.ofPubKeyAccount(self);
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            forks
                .genesisFork()
                .engineRules()
                .config()
                .minimumStake()
                .toSubunits()
                .subtract(UInt128.ONE),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifierPreparedStake(selfAddress, self));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails()).isInstanceOf(BelowMinimumStakeError.class);
  }

  @Test
  public void staking_tokens_to_non_owned_validator_should_fail() throws Exception {
    // Arrange
    start();

    // Act
    var selfAddress = REAddr.ofPubKeyAccount(self);
    var otherKey = PrivateKeys.ofNumeric(2).getPublicKey();
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            getLiquidAmount().toSubunits().subtract(Amount.ofTokens(1).toSubunits()),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifierPreparedStake(selfAddress, otherKey));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails()).isInstanceOf(NotValidatorOwnerError.class);
  }

  @Test
  public void withdrawing_staked_tokens_should_fail() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        buildTransfer(
            coreModelMapper.nativeToken(),
            getStakeAmount().toSubunits(),
            coreModelMapper.entityIdentifier(self).subEntity(new SubEntity().address("system")),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails())
        .isInstanceOf(ResourceWithdrawOperationNotSupportedByEntityError.class);
  }

  @Test
  public void unstaking_tokens_should_work() throws Exception {
    // Arrange
    start();

    // Act
    var selfAddress = REAddr.ofPubKeyAccount(self);
    var request =
        buildTransfer(
            coreModelMapper.stakeUnit(self),
            getStakeAmount().toSubunits(),
            coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(self)),
            coreModelMapper.entityIdentifierPreparedUnstake(selfAddress));
    var response = handleRequestWithExpectedResponse(sut, request, ConstructionBuildResponse.class);

    // Assert
    assertThat(Bytes.fromHexString(response.getPayloadToSign())).isNotNull();
    assertThat(Bytes.fromHexString(response.getUnsignedTransaction())).isNotNull();
  }
}
