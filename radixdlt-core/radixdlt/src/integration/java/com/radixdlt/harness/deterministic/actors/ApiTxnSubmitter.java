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

package com.radixdlt.harness.deterministic.actors;

import com.google.inject.Key;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.openapitools.model.AboveMaximumValidatorFeeIncreaseError;
import com.radixdlt.api.core.openapitools.model.BelowMinimumStakeError;
import com.radixdlt.api.core.openapitools.model.CoreError;
import com.radixdlt.api.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.openapitools.model.MempoolFullError;
import com.radixdlt.api.core.openapitools.model.NotEnoughResourcesError;
import com.radixdlt.api.core.openapitools.model.NotValidatorOwnerError;
import com.radixdlt.api.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.openapitools.model.TokenData;
import com.radixdlt.api.core.openapitools.model.TokenResourceIdentifier;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.harness.deterministic.DeterministicActor;
import com.radixdlt.harness.deterministic.actors.actions.BurnTokens;
import com.radixdlt.harness.deterministic.actors.actions.CreateTokenDefinition;
import com.radixdlt.harness.deterministic.actors.actions.MintTokens;
import com.radixdlt.harness.deterministic.actors.actions.NodeTransactionAction;
import com.radixdlt.harness.deterministic.actors.actions.RegisterValidator;
import com.radixdlt.harness.deterministic.actors.actions.SetAllowDelegationFlag;
import com.radixdlt.harness.deterministic.actors.actions.SetValidatorFee;
import com.radixdlt.harness.deterministic.actors.actions.SetValidatorOwner;
import com.radixdlt.harness.deterministic.actors.actions.StakeTokens;
import com.radixdlt.harness.deterministic.actors.actions.TransferTokens;
import com.radixdlt.harness.deterministic.actors.actions.UnstakeStakeUnits;
import com.radixdlt.utils.UInt256;
import java.util.Random;
import java.util.Set;

/** Submits a random transaction through a random node in a deterministic test. */
public final class ApiTxnSubmitter implements DeterministicActor {
  private static final Set<Class<? extends CoreError>> OKAY_ERRORS =
      Set.of(
          MempoolFullError.class,
          NotEnoughResourcesError.class,
          AboveMaximumValidatorFeeIncreaseError.class,
          BelowMinimumStakeError.class,
          NotValidatorOwnerError.class);
  private int tokenId = 0;

  private Amount nextAmount(Random random) {
    return Amount.ofTokens(random.nextInt(10) * 10 + 1);
  }

  private TransferTokens transferTokens(
      NodeApiClient nodeClient, EntityIdentifier to, Random random) throws Exception {
    var publicKey = nodeClient.getPublicKey();
    var accountIdentifier = nodeClient.deriveAccount(publicKey);
    var response = nodeClient.getEntity(accountIdentifier);
    var tokenTypes =
        response.getBalances().stream()
            .map(ResourceAmount::getResourceIdentifier)
            .filter(TokenResourceIdentifier.class::isInstance)
            .map(TokenResourceIdentifier.class::cast)
            .toList();

    TokenResourceIdentifier tokenResourceIdentifier;
    if (tokenTypes.isEmpty()) {
      tokenResourceIdentifier = nodeClient.nativeToken();
    } else {
      var nextIndex = random.nextInt(tokenTypes.size());
      tokenResourceIdentifier = tokenTypes.get(nextIndex);
    }

    return new TransferTokens(nextAmount(random), tokenResourceIdentifier, to);
  }

  private TokenResourceIdentifier findTokenClientOwns(NodeApiClient nodeClient, Random random)
      throws Exception {
    var publicKey = nodeClient.getPublicKey();
    var accountIdentifier = nodeClient.deriveAccount(publicKey);
    var response = nodeClient.getEntity(accountIdentifier);
    var tokenTypes =
        response.getBalances().stream()
            .map(ResourceAmount::getResourceIdentifier)
            .filter(TokenResourceIdentifier.class::isInstance)
            .map(TokenResourceIdentifier.class::cast)
            .filter(
                t -> {
                  try {
                    var entityResponse =
                        nodeClient.getEntity(new EntityIdentifier().address(t.getRri()));
                    return entityResponse.getDataObjects().stream()
                        .filter(TokenData.class::isInstance)
                        .map(TokenData.class::cast)
                        .filter(d -> d.getOwner() != null)
                        .anyMatch(d -> d.getOwner().equals(accountIdentifier));
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                })
            .toList();

    if (tokenTypes.isEmpty()) {
      return null;
    } else {
      var nextIndex = random.nextInt(tokenTypes.size());
      return tokenTypes.get(nextIndex);
    }
  }

  private MintTokens mintTokens(NodeApiClient nodeClient, EntityIdentifier to, Random random)
      throws Exception {
    var tokenResourceIdentifier = findTokenClientOwns(nodeClient, random);
    if (tokenResourceIdentifier == null) {
      return null;
    }

    return new MintTokens(nextAmount(random), tokenResourceIdentifier, to);
  }

  private BurnTokens burnTokens(NodeApiClient nodeClient, Random random) throws Exception {
    var tokenResourceIdentifier = findTokenClientOwns(nodeClient, random);
    if (tokenResourceIdentifier == null) {
      return null;
    }
    var publicKey = nodeClient.getPublicKey();
    var accountIdentifier = nodeClient.deriveAccount(publicKey);
    return new BurnTokens(nextAmount(random), tokenResourceIdentifier, accountIdentifier);
  }

  private CreateTokenDefinition createTokenDefinition(
      NodeApiClient nodeClient, EntityIdentifier to, Random random) throws Exception {
    var publicKey = nodeClient.getPublicKey();
    var owner = nodeClient.deriveAccount(publicKey);
    var uint256Bytes = new byte[UInt256.BYTES];
    random.nextBytes(uint256Bytes);
    var amount = UInt256.from(uint256Bytes);
    if (random.nextBoolean()) {
      return CreateTokenDefinition.mutableTokenSupply("test" + tokenId++, amount, owner, to);
    } else {
      return CreateTokenDefinition.fixedTokenSupply("test" + tokenId++, amount, to);
    }
  }

  private String submitAction(NodeApiClient nodeClient, NodeTransactionAction action) {
    try {
      nodeClient.submit(action, false);
    } catch (CoreApiException e) {
      // Throw error if not expected
      if (!OKAY_ERRORS.contains(e.toError().getDetails().getClass())) {
        throw new IllegalStateException(String.format("Invalid failure on action %s", action), e);
      }

      var errorName = e.toError().getDetails().getClass().getSimpleName();
      return String.format(
          "BuildError{action=%s error=%s}", action.getClass().getSimpleName(), errorName);
    } catch (Exception e) {
      throw new IllegalStateException(String.format("Invalid failure on action %s", action), e);
    }

    return String.format("Submitted{action=%s}", action.getClass().getSimpleName());
  }

  @Override
  public String execute(MultiNodeDeterministicRunner runner, Random random) throws Exception {
    int size = runner.getSize();
    var nodeIndex = random.nextInt(size);
    var nodeInjector = runner.getNode(nodeIndex);
    var nodeClient = nodeInjector.getInstance(NodeApiClient.class);
    var otherNodeIndex = random.nextInt(size);
    var otherKey =
        runner.getNode(otherNodeIndex).getInstance(Key.get(ECPublicKey.class, Self.class));
    var next = random.nextInt(11);

    // Don't let the last validator unregister
    if (next == 4 && nodeIndex <= 0) {
      return "Skipped";
    }

    NodeTransactionAction action =
        switch (next) {
          case 0 -> transferTokens(nodeClient, nodeClient.deriveAccount(otherKey), random);
          case 1 -> new StakeTokens(nextAmount(random), nodeClient.deriveValidator(otherKey));
          case 2 -> new UnstakeStakeUnits(
              nextAmount(random), nodeClient.deriveValidator(otherKey).getAddress());
          case 3 -> new RegisterValidator(true);
          case 4 -> new RegisterValidator(false);
          case 5 -> new SetValidatorFee(
              random.nextInt(ValidatorUpdateRakeConstraintScrypt.RAKE_MAX + 1));
          case 6 -> new SetValidatorOwner(nodeClient.deriveAccount(otherKey));
          case 7 -> new SetAllowDelegationFlag(random.nextBoolean());
          case 8 -> createTokenDefinition(nodeClient, nodeClient.deriveAccount(otherKey), random);
          case 9 -> mintTokens(nodeClient, nodeClient.deriveAccount(otherKey), random);
          case 10 -> burnTokens(nodeClient, random);
          default -> throw new IllegalStateException("Unexpected value: " + next);
        };

    if (action == null) {
      return "Skipped";
    }

    return submitAction(nodeClient, action);
  }
}
