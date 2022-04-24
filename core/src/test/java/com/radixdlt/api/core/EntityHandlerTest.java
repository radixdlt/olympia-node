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
import com.radixdlt.api.core.handlers.EntityHandler;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.model.SubstateTypeMapping;
import com.radixdlt.api.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.openapitools.model.EntityRequest;
import com.radixdlt.api.core.openapitools.model.EntityResponse;
import com.radixdlt.api.core.openapitools.model.InvalidAddressError;
import com.radixdlt.api.core.openapitools.model.InvalidSubEntityError;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.PreparedValidatorFee;
import com.radixdlt.api.core.openapitools.model.PreparedValidatorOwner;
import com.radixdlt.api.core.openapitools.model.PreparedValidatorRegistered;
import com.radixdlt.api.core.openapitools.model.SubEntity;
import com.radixdlt.api.core.openapitools.model.TokenData;
import com.radixdlt.api.core.openapitools.model.TokenMetadata;
import com.radixdlt.api.core.openapitools.model.UnclaimedRadixEngineAddress;
import com.radixdlt.api.core.openapitools.model.UnexpectedError;
import com.radixdlt.api.core.openapitools.model.ValidatorAllowDelegation;
import com.radixdlt.api.core.openapitools.model.ValidatorBFTData;
import com.radixdlt.api.core.openapitools.model.ValidatorData;
import com.radixdlt.api.core.openapitools.model.ValidatorMetadata;
import com.radixdlt.api.core.openapitools.model.ValidatorSystemMetadata;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.utils.Bytes;
import org.junit.Test;

// TODO: Refactor so that every entity is just a parameter in a parametrized test
public class EntityHandlerTest extends ApiTest {
  @Inject private EntityHandler sut;
  @Inject private CoreModelMapper coreModelMapper;
  @Inject @Genesis private VerifiedTxnsAndProof genesis;

  @Test
  public void retrieve_system_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(new EntityIdentifier().address("system"));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getBalances()).isEmpty();
    assertThat(response.getDataObjects()).isNotEmpty();
  }

  @Test
  public void retrieve_native_token_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifier(REAddr.ofNativeToken(), "xrd"));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getBalances()).isEmpty();
    assertThat(response.getDataObjects()).hasAtLeastOneElementOfType(TokenData.class);
    assertThat(response.getDataObjects()).hasAtLeastOneElementOfType(TokenMetadata.class);
  }

  @Test
  public void retrieve_non_existent_token_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var tokenAddress = REAddr.ofHashedKey(selfKey(), "test");
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifier(tokenAddress, "test"));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getBalances()).isEmpty();
    assertThat(response.getDataObjects())
        .containsExactly(
            new UnclaimedRadixEngineAddress()
                .type(SubstateTypeMapping.getName(SubstateTypeId.UNCLAIMED_READDR)));
  }

  @Test
  public void retrieve_account_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        new EntityRequest()
            .networkIdentifier(networkIdentifier())
            .entityIdentifier(coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(selfKey())));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getBalances())
        .containsExactlyInAnyOrder(
            coreModelMapper.nativeTokenAmount(getLiquidAmount().toSubunits()),
            coreModelMapper.stakeUnitAmount(selfKey(), getStakeAmount().toSubunits()));
  }

  @Test
  public void retrieve_validator_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifier(selfKey()));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getDataObjects())
        .hasOnlyElementsOfTypes(
            ValidatorAllowDelegation.class,
            ValidatorMetadata.class,
            ValidatorSystemMetadata.class,
            PreparedValidatorOwner.class,
            PreparedValidatorFee.class,
            PreparedValidatorRegistered.class);
    assertThat(response.getBalances()).isEmpty();
  }

  @Test
  public void retrieve_validator_system_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifierValidatorSystem(selfKey()));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getDataObjects())
        .hasOnlyElementsOfTypes(ValidatorBFTData.class, ValidatorData.class);
    assertThat(response.getBalances())
        .containsExactly(coreModelMapper.nativeTokenAmount(getStakeAmount().toSubunits()));
  }

  @Test
  public void retrieve_prepared_stake_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var address = REAddr.ofPubKeyAccount(selfKey());
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifierPreparedStake(address, selfKey()));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getDataObjects()).isEmpty();
    assertThat(response.getBalances()).isEmpty();
  }

  @Test
  public void retrieve_prepared_unstake_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var address = REAddr.ofPubKeyAccount(selfKey());
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifierPreparedUnstake(address));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getDataObjects()).isEmpty();
    assertThat(response.getBalances()).isEmpty();
  }

  @Test
  public void retrieve_exiting_stake_entity_on_genesis() throws Exception {
    // Arrange
    start();

    // Act
    var address = REAddr.ofPubKeyAccount(selfKey());
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(coreModelMapper.entityIdentifierExitingStake(address, selfKey(), 1));
    var response = handleRequestWithExpectedResponse(sut, request, EntityResponse.class);

    // Assert
    var stateAccumulator = response.getStateIdentifier().getTransactionAccumulator();
    var genesisAccumulator =
        genesis.getProof().getAccumulatorState().getAccumulatorHash().asBytes();
    assertThat(stateAccumulator).isEqualTo(Bytes.toHexString(genesisAccumulator));
    assertThat(response.getDataObjects()).isEmpty();
    assertThat(response.getBalances()).isEmpty();
  }

  @Test
  public void retrieve_invalid_entity_should_throw() throws Exception {
    // Arrange
    start();

    // Act
    // Assert
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(new EntityIdentifier().address("some_garbage_address"));
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    assertThat(response.getDetails()).isInstanceOf(InvalidAddressError.class);
  }

  @Test
  public void retrieve_invalid_sub_entity_should_throw() throws Exception {
    // Arrange
    start();

    // Act
    var address = REAddr.ofPubKeyAccount(selfKey());
    var invalidSubEntity =
        coreModelMapper
            .entityIdentifier(address)
            .subEntity(new SubEntity().address("prepared_stakes"));
    var request =
        new EntityRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .entityIdentifier(invalidSubEntity);
    var response = handleRequestWithExpectedResponse(sut, request, UnexpectedError.class);

    // Assert
    assertThat(response.getDetails()).isInstanceOf(InvalidSubEntityError.class);
  }
}
