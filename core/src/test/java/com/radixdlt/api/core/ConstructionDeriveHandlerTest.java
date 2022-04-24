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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Inject;
import com.radixdlt.api.ApiTest;
import com.radixdlt.api.core.handlers.ConstructionDeriveHandler;
import com.radixdlt.api.core.model.CoreApiErrorCode;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataAccount;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataExitingUnstakes;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataPreparedStakes;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataPreparedUnstakes;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataToken;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataValidator;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataValidatorSystem;
import com.radixdlt.api.core.openapitools.model.InvalidPublicKeyError;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.NotValidatorEntityError;
import com.radixdlt.api.core.openapitools.model.PublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.PrivateKeys;
import org.junit.Test;

public class ConstructionDeriveHandlerTest extends ApiTest {

  @Inject private ConstructionDeriveHandler sut;
  @Inject private CoreModelMapper coreModelMapper;

  @Test
  public void derive_account_request_should_return_account_entity_identifier()
      throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(new ConstructionDeriveRequestMetadataAccount().type("Account"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(publicKey)));
  }

  @Test
  public void derive_validator_request_should_return_validator_entity_identifier()
      throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(new ConstructionDeriveRequestMetadataValidator().type("Validator"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(coreModelMapper.entityIdentifier(publicKey));
  }

  @Test
  public void derive_token_request_should_return_token_entity_identifier() throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(new ConstructionDeriveRequestMetadataToken().symbol("test").type("Token"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(coreModelMapper.entityIdentifier(REAddr.ofHashedKey(publicKey, "test"), "test"));
  }

  @Test
  public void derive_prepared_stakes_should_return_entity_identifier() throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    var validatorKey = PrivateKeys.ofNumeric(3).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(
                new ConstructionDeriveRequestMetadataPreparedStakes()
                    .validator(coreModelMapper.entityIdentifier(validatorKey))
                    .type("PreparedStakes"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(
            coreModelMapper.entityIdentifierPreparedStake(
                REAddr.ofPubKeyAccount(publicKey), validatorKey));
  }

  @Test
  public void derive_prepared_stakes_with_invalid_validator_should_return_error() {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    var validatorKey = PrivateKeys.ofNumeric(3).getPublicKey();
    start();

    // Act
    // Assert
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(
                new ConstructionDeriveRequestMetadataPreparedStakes()
                    .validator(
                        coreModelMapper.entityIdentifier(REAddr.ofPubKeyAccount(validatorKey)))
                    .type("PreparedStakes"));
    assertThatThrownBy(() -> sut.handleRequest(request))
        .isInstanceOfSatisfying(
            CoreApiException.class,
            e -> {
              assertThat(e.toError().getDetails()).isInstanceOf(NotValidatorEntityError.class);
            });
  }

  @Test
  public void derive_validator_system_should_return_entity_identifier() throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(
                new ConstructionDeriveRequestMetadataValidatorSystem().type("ValidatorSystem"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(coreModelMapper.entityIdentifierValidatorSystem(publicKey));
  }

  @Test
  public void derive_prepared_unstakes_should_return_entity_identifier() throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(
                new ConstructionDeriveRequestMetadataPreparedUnstakes().type("PreparedUnstakes"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(
            coreModelMapper.entityIdentifierPreparedUnstake(REAddr.ofPubKeyAccount(publicKey)));
  }

  @Test
  public void derive_exiting_unstakes_should_return_entity_identifier() throws CoreApiException {
    // Arrange
    var publicKey = PrivateKeys.ofNumeric(2).getPublicKey();
    var validatorKey = PrivateKeys.ofNumeric(3).getPublicKey();
    long epochUnlock = 236L;
    start();

    // Act
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(networkIdentifier())
            .publicKey(coreModelMapper.publicKey(publicKey))
            .metadata(
                new ConstructionDeriveRequestMetadataExitingUnstakes()
                    .validator(coreModelMapper.entityIdentifier(validatorKey))
                    .epochUnlock(epochUnlock)
                    .type("ExitingUnstakes"));
    var response = sut.handleRequest(request);

    // Assert
    assertThat(response.getEntityIdentifier())
        .isEqualTo(
            coreModelMapper.entityIdentifierExitingStake(
                REAddr.ofPubKeyAccount(publicKey), validatorKey, epochUnlock));
  }

  @Test
  public void invalid_public_key_should_throw_exception() {
    // Arrange
    start();

    // Act
    // Assert
    var request =
        new ConstructionDeriveRequest()
            .networkIdentifier(new NetworkIdentifier().network("localnet"))
            .publicKey(new PublicKey().hex("deadbeaddeadbead"))
            .metadata(new ConstructionDeriveRequestMetadataToken().symbol("test").type("Token"));
    assertThatThrownBy(() -> sut.handleRequest(request))
        .isInstanceOfSatisfying(
            CoreApiException.class,
            e -> {
              var error = e.toError();
              assertThat(error.getDetails()).isInstanceOf(InvalidPublicKeyError.class);
              assertThat(error.getCode()).isEqualTo(CoreApiErrorCode.BAD_REQUEST.getErrorCode());
            });
  }
}
