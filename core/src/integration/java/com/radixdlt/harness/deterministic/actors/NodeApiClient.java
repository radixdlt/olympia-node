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

import com.google.inject.Inject;
import com.radixdlt.api.core.handlers.ConstructionBuildHandler;
import com.radixdlt.api.core.handlers.ConstructionDeriveHandler;
import com.radixdlt.api.core.handlers.ConstructionSubmitHandler;
import com.radixdlt.api.core.handlers.EngineConfigurationHandler;
import com.radixdlt.api.core.handlers.EngineStatusHandler;
import com.radixdlt.api.core.handlers.EntityHandler;
import com.radixdlt.api.core.handlers.ForksVotingResultsHandler;
import com.radixdlt.api.core.handlers.KeyListHandler;
import com.radixdlt.api.core.handlers.KeySignHandler;
import com.radixdlt.api.core.handlers.NetworkConfigurationHandler;
import com.radixdlt.api.core.handlers.NetworkStatusHandler;
import com.radixdlt.api.core.handlers.TransactionsHandler;
import com.radixdlt.api.core.model.CoreApiException;
import com.radixdlt.api.core.model.CoreModelMapper;
import com.radixdlt.api.core.openapitools.model.CommittedTransaction;
import com.radixdlt.api.core.openapitools.model.CommittedTransactionsRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionBuildRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequest;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadata;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataAccount;
import com.radixdlt.api.core.openapitools.model.ConstructionDeriveRequestMetadataValidator;
import com.radixdlt.api.core.openapitools.model.ConstructionSubmitRequest;
import com.radixdlt.api.core.openapitools.model.EngineConfigurationRequest;
import com.radixdlt.api.core.openapitools.model.EngineStatusRequest;
import com.radixdlt.api.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.openapitools.model.EntityRequest;
import com.radixdlt.api.core.openapitools.model.EntityResponse;
import com.radixdlt.api.core.openapitools.model.ForkVotingResult;
import com.radixdlt.api.core.openapitools.model.ForksVotingResultsRequest;
import com.radixdlt.api.core.openapitools.model.KeyListRequest;
import com.radixdlt.api.core.openapitools.model.KeySignRequest;
import com.radixdlt.api.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.openapitools.model.NetworkStatusRequest;
import com.radixdlt.api.core.openapitools.model.PartialStateIdentifier;
import com.radixdlt.api.core.openapitools.model.PublicKey;
import com.radixdlt.api.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.openapitools.model.StateIdentifier;
import com.radixdlt.api.core.openapitools.model.TokenResourceIdentifier;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.harness.deterministic.actors.actions.NodeTransactionAction;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.List;

/** Helper class to execute api commands */
final class NodeApiClient {
  private final EntityHandler entityHandler;
  private final NetworkConfigurationHandler networkConfigurationHandler;
  private final NetworkStatusHandler networkStatusHandler;
  private final KeyListHandler keyListHandler;
  private final ConstructionBuildHandler constructionBuildHandler;
  private final KeySignHandler keySignHandler;
  private final ConstructionDeriveHandler constructionDeriveHandler;
  private final ConstructionSubmitHandler constructionSubmitHandler;
  private final EngineConfigurationHandler engineConfigurationHandler;
  private final EngineStatusHandler engineStatusHandler;
  private final ForksVotingResultsHandler forksVotingResultsHandler;
  private final TransactionsHandler transactionsHandler;
  private final CoreModelMapper coreModelMapper;

  @Inject
  NodeApiClient(
      NetworkConfigurationHandler networkConfigurationHandler,
      NetworkStatusHandler networkStatusHandler,
      EntityHandler entityHandler,
      KeyListHandler keyListHandler,
      ConstructionBuildHandler constructionBuildHandler,
      ConstructionDeriveHandler constructionDeriveHandler,
      KeySignHandler keySignHandler,
      ConstructionSubmitHandler constructionSubmitHandler,
      EngineConfigurationHandler engineConfigurationHandler,
      EngineStatusHandler engineStatusHandler,
      ForksVotingResultsHandler forksVotingResultsHandler,
      TransactionsHandler transactionsHandler,
      CoreModelMapper coreModelMapper) {
    this.networkConfigurationHandler = networkConfigurationHandler;
    this.networkStatusHandler = networkStatusHandler;
    this.entityHandler = entityHandler;
    this.keyListHandler = keyListHandler;
    this.constructionDeriveHandler = constructionDeriveHandler;
    this.constructionBuildHandler = constructionBuildHandler;
    this.keySignHandler = keySignHandler;
    this.constructionSubmitHandler = constructionSubmitHandler;
    this.engineConfigurationHandler = engineConfigurationHandler;
    this.engineStatusHandler = engineStatusHandler;
    this.forksVotingResultsHandler = forksVotingResultsHandler;
    this.transactionsHandler = transactionsHandler;
    this.coreModelMapper = coreModelMapper;
  }

  private NetworkIdentifier networkIdentifier() {
    var networkResponse = networkConfigurationHandler.handleRequest((Void) null);
    return networkResponse.getNetworkIdentifier();
  }

  public TokenResourceIdentifier nativeToken() {
    return coreModelMapper.nativeToken();
  }

  public PublicKey selfPublicKey() throws Exception {
    var keyListResponse =
        keyListHandler.handleRequest(new KeyListRequest().networkIdentifier(networkIdentifier()));
    return keyListResponse.getPublicKeys().get(0).getPublicKey();
  }

  public EntityIdentifier selfDerive(ConstructionDeriveRequestMetadata metadata) {
    try {
      var response =
          constructionDeriveHandler.handleRequest(
              new ConstructionDeriveRequest()
                  .networkIdentifier(networkIdentifier())
                  .publicKey(selfPublicKey())
                  .metadata(metadata));
      return response.getEntityIdentifier();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public EntityIdentifier deriveValidator(ECPublicKey key) throws Exception {
    var response =
        this.constructionDeriveHandler.handleRequest(
            new ConstructionDeriveRequest()
                .networkIdentifier(networkIdentifier())
                .publicKey(coreModelMapper.publicKey(key))
                .metadata(new ConstructionDeriveRequestMetadataValidator()));
    return response.getEntityIdentifier();
  }

  public EntityIdentifier deriveAccount(PublicKey publicKey) throws Exception {
    var response =
        this.constructionDeriveHandler.handleRequest(
            new ConstructionDeriveRequest()
                .networkIdentifier(networkIdentifier())
                .publicKey(publicKey)
                .metadata(new ConstructionDeriveRequestMetadataAccount()));
    return response.getEntityIdentifier();
  }

  public EntityIdentifier deriveAccount(ECPublicKey key) throws Exception {
    return deriveAccount(coreModelMapper.publicKey(key));
  }

  public StateIdentifier getStateIdentifier() throws Exception {
    var response =
        networkStatusHandler.handleRequest(
            new NetworkStatusRequest().networkIdentifier(networkIdentifier()));
    return response.getCurrentStateIdentifier();
  }

  public PublicKey getPublicKey() throws Exception {
    var response =
        keyListHandler.handleRequest(new KeyListRequest().networkIdentifier(networkIdentifier()));
    return response.getPublicKeys().get(0).getPublicKey();
  }

  public EpochView getEpochView() throws Exception {
    var statusResponse =
        engineStatusHandler.handleRequest(
            new EngineStatusRequest().networkIdentifier(networkIdentifier()));
    var epoch = statusResponse.getEngineStateIdentifier().getEpoch();
    var round = statusResponse.getEngineStateIdentifier().getRound();
    return EpochView.of(epoch, View.of(round));
  }

  public UInt256 getRewardsPerProposal() throws Exception {
    var response =
        this.engineConfigurationHandler.handleRequest(
            new EngineConfigurationRequest().networkIdentifier(networkIdentifier()));
    return UInt256.from(
        response.getForks().get(0).getEngineConfiguration().getRewardsPerProposal().getValue());
  }

  public long getRoundsPerEpoch() throws Exception {
    var response =
        this.engineConfigurationHandler.handleRequest(
            new EngineConfigurationRequest().networkIdentifier(networkIdentifier()));
    return response.getForks().get(0).getEngineConfiguration().getMaximumRoundsPerEpoch();
  }

  public long unstakingDelayEpochLength() {
    try {
      return engineConfigurationHandler
          .handleRequest(
              new EngineConfigurationRequest()
                  .networkIdentifier(new NetworkIdentifier().network("localnet")))
          .getForks()
          .get(0)
          .getEngineConfiguration()
          .getUnstakingDelayEpochLength();
    } catch (CoreApiException e) {
      throw new IllegalStateException(e);
    }
  }

  public EntityResponse getEntity(EntityIdentifier entityIdentifier) {
    try {
      return entityHandler.handleRequest(
          new EntityRequest()
              .networkIdentifier(new NetworkIdentifier().network("localnet"))
              .entityIdentifier(entityIdentifier));
    } catch (CoreApiException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<ResourceAmount> getUnstakes(REAddr addr, ECPublicKey validatorKey) {
    var networkIdentifier = new NetworkIdentifier().network("localnet");
    var unstakingDelayEpochLength = unstakingDelayEpochLength();
    var unstakes = new ArrayList<ResourceAmount>();
    try {
      var statusResponse =
          engineStatusHandler.handleRequest(
              new EngineStatusRequest().networkIdentifier(networkIdentifier));
      var curEpoch = statusResponse.getEngineStateIdentifier().getEpoch();
      var maxEpoch = curEpoch + unstakingDelayEpochLength + 1;

      for (long epochUnstake = curEpoch; epochUnstake <= maxEpoch; epochUnstake++) {
        var response =
            entityHandler.handleRequest(
                new EntityRequest()
                    .networkIdentifier(networkIdentifier)
                    .entityIdentifier(
                        coreModelMapper.entityIdentifierExitingStake(
                            addr, validatorKey, epochUnstake)));
        unstakes.addAll(response.getBalances());
      }
    } catch (CoreApiException e) {
      throw new IllegalStateException(e);
    }

    return unstakes;
  }

  public List<CommittedTransaction> getTransactions(long stateVersion, long limit)
      throws Exception {
    var response =
        transactionsHandler.handleRequest(
            new CommittedTransactionsRequest()
                .networkIdentifier(networkIdentifier())
                .stateIdentifier(new PartialStateIdentifier().stateVersion(stateVersion))
                .limit(limit));
    return response.getTransactions();
  }

  public void submit(NodeTransactionAction action, boolean disableResourceAllocateAndDestroy)
      throws Exception {
    var networkIdentifier = networkIdentifier();
    var engineConfigurationResponse =
        engineConfigurationHandler.handleRequest(
            new EngineConfigurationRequest().networkIdentifier(networkIdentifier));
    var keyListResponse =
        keyListHandler.handleRequest(new KeyListRequest().networkIdentifier(networkIdentifier));
    var nodePublicKey = keyListResponse.getPublicKeys().get(0).getPublicKey();
    var configuration = engineConfigurationResponse.getForks().get(0).getEngineConfiguration();

    var accountIdentifier = deriveAccount(nodePublicKey);
    var operationGroups = action.toOperationGroups(configuration, this::selfDerive);

    var buildRequest =
        new ConstructionBuildRequest()
            .networkIdentifier(networkIdentifier)
            .feePayer(accountIdentifier)
            .operationGroups(operationGroups)
            .disableResourceAllocateAndDestroy(disableResourceAllocateAndDestroy);
    var buildResponse = constructionBuildHandler.handleRequest(buildRequest);
    var unsignedTransaction = buildResponse.getUnsignedTransaction();

    var response =
        keySignHandler.handleRequest(
            new KeySignRequest()
                .networkIdentifier(networkIdentifier)
                .publicKey(nodePublicKey)
                .unsignedTransaction(unsignedTransaction));

    constructionSubmitHandler.handleRequest(
        new ConstructionSubmitRequest()
            .networkIdentifier(networkIdentifier)
            .signedTransaction(response.getSignedTransaction()));
  }

  public List<ForkVotingResult> forksVotingResults(long epoch) throws CoreApiException {
    return forksVotingResultsHandler
        .handleRequest(new ForksVotingResultsRequest().epoch(epoch))
        .getForksVotingResults();
  }
}
