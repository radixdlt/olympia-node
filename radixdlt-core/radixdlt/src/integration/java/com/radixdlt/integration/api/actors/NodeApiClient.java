/*
 * Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
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

package com.radixdlt.integration.api.actors;

import com.google.inject.Inject;
import com.radixdlt.api.core.core.CoreApiException;
import com.radixdlt.api.core.core.CoreModelMapper;
import com.radixdlt.api.core.core.handlers.ConstructionBuildHandler;
import com.radixdlt.api.core.core.handlers.ConstructionDeriveHandler;
import com.radixdlt.api.core.core.handlers.ConstructionSubmitHandler;
import com.radixdlt.api.core.core.handlers.EngineConfigurationHandler;
import com.radixdlt.api.core.core.handlers.EngineStatusHandler;
import com.radixdlt.api.core.core.handlers.EntityHandler;
import com.radixdlt.api.core.core.handlers.NetworkConfigurationHandler;
import com.radixdlt.api.core.core.handlers.NetworkStatusHandler;
import com.radixdlt.api.core.core.handlers.NodeIdentifiersHandler;
import com.radixdlt.api.core.core.handlers.NodeSignHandler;
import com.radixdlt.api.core.core.handlers.TransactionsHandler;
import com.radixdlt.api.core.core.openapitools.model.AboveMaximumValidatorFeeIncreaseError;
import com.radixdlt.api.core.core.openapitools.model.BelowMinimumStakeError;
import com.radixdlt.api.core.core.openapitools.model.CommittedTransaction;
import com.radixdlt.api.core.core.openapitools.model.CommittedTransactionsRequest;
import com.radixdlt.api.core.core.openapitools.model.ConstructionBuildRequest;
import com.radixdlt.api.core.core.openapitools.model.ConstructionDeriveRequest;
import com.radixdlt.api.core.core.openapitools.model.ConstructionDeriveRequestMetadataAccount;
import com.radixdlt.api.core.core.openapitools.model.ConstructionDeriveRequestMetadataValidator;
import com.radixdlt.api.core.core.openapitools.model.ConstructionSubmitRequest;
import com.radixdlt.api.core.core.openapitools.model.EngineConfigurationRequest;
import com.radixdlt.api.core.core.openapitools.model.EngineStatusRequest;
import com.radixdlt.api.core.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.core.openapitools.model.EntityRequest;
import com.radixdlt.api.core.core.openapitools.model.EntityResponse;
import com.radixdlt.api.core.core.openapitools.model.MempoolFullError;
import com.radixdlt.api.core.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.core.openapitools.model.NetworkStatusRequest;
import com.radixdlt.api.core.core.openapitools.model.NodeIdentifiersRequest;
import com.radixdlt.api.core.core.openapitools.model.NodeSignRequest;
import com.radixdlt.api.core.core.openapitools.model.NotEnoughResourcesError;
import com.radixdlt.api.core.core.openapitools.model.NotValidatorOwnerError;
import com.radixdlt.api.core.core.openapitools.model.PartialStateIdentifier;
import com.radixdlt.api.core.core.openapitools.model.ResourceAmount;
import com.radixdlt.api.core.core.openapitools.model.StateIdentifier;
import com.radixdlt.api.core.core.openapitools.model.TokenResourceIdentifier;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.integration.api.actors.actions.NodeTransactionAction;
import com.radixdlt.utils.UInt256;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class NodeApiClient {
	private final EntityHandler entityHandler;
	private final NetworkConfigurationHandler networkConfigurationHandler;
	private final NetworkStatusHandler networkStatusHandler;
	private final NodeIdentifiersHandler nodeIdentifiersHandler;
	private final ConstructionBuildHandler constructionBuildHandler;
	private final NodeSignHandler nodeSignHandler;
	private final ConstructionDeriveHandler constructionDeriveHandler;
	private final ConstructionSubmitHandler constructionSubmitHandler;
	private final EngineConfigurationHandler engineConfigurationHandler;
	private final EngineStatusHandler engineStatusHandler;
	private final TransactionsHandler transactionsHandler;
	private final CoreModelMapper coreModelMapper;

	@Inject
	NodeApiClient(
		NetworkConfigurationHandler networkConfigurationHandler,
		NetworkStatusHandler networkStatusHandler,
		EntityHandler entityHandler,
		NodeIdentifiersHandler nodeIdentifiersHandler,
		ConstructionBuildHandler constructionBuildHandler,
		ConstructionDeriveHandler constructionDeriveHandler,
		NodeSignHandler nodeSignHandler,
		ConstructionSubmitHandler constructionSubmitHandler,
		EngineConfigurationHandler engineConfigurationHandler,
		EngineStatusHandler engineStatusHandler,
		TransactionsHandler transactionsHandler,
		CoreModelMapper coreModelMapper
	) {
		this.networkConfigurationHandler = networkConfigurationHandler;
		this.networkStatusHandler = networkStatusHandler;
		this.entityHandler = entityHandler;
		this.nodeIdentifiersHandler = nodeIdentifiersHandler;
		this.constructionDeriveHandler = constructionDeriveHandler;
		this.constructionBuildHandler = constructionBuildHandler;
		this.nodeSignHandler = nodeSignHandler;
		this.constructionSubmitHandler = constructionSubmitHandler;
		this.engineConfigurationHandler = engineConfigurationHandler;
		this.engineStatusHandler = engineStatusHandler;
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

	public EntityIdentifier deriveValidator(ECPublicKey key) throws Exception {
		var response = this.constructionDeriveHandler.handleRequest(new ConstructionDeriveRequest()
			.networkIdentifier(networkIdentifier())
			.publicKey(coreModelMapper.publicKey(key))
			.metadata(new ConstructionDeriveRequestMetadataValidator())
		);
		return response.getEntityIdentifier();
	}

	public EntityIdentifier deriveAccount(ECPublicKey key) throws Exception {
		var response = this.constructionDeriveHandler.handleRequest(new ConstructionDeriveRequest()
			.networkIdentifier(networkIdentifier())
			.publicKey(coreModelMapper.publicKey(key))
			.metadata(new ConstructionDeriveRequestMetadataAccount())
		);
		return response.getEntityIdentifier();
	}

	public StateIdentifier getStateIdentifier() throws Exception {
		var response = networkStatusHandler.handleRequest(new NetworkStatusRequest().networkIdentifier(networkIdentifier()));
		return response.getCurrentStateIdentifier();
	}

	public EpochView getEpochView() throws Exception {
		var statusResponse = engineStatusHandler
			.handleRequest(new EngineStatusRequest().networkIdentifier(networkIdentifier()));
		var epoch = statusResponse.getEngineStateIdentifier().getEpoch();
		var round = statusResponse.getEngineStateIdentifier().getRound();
		return EpochView.of(epoch, View.of(round));
	}

	public UInt256 getRewardsPerProposal() throws Exception {
		var response = this.engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
			.networkIdentifier(networkIdentifier())
		);
		return UInt256.from(response.getForks().get(0).getEngineConfiguration().getRewardsPerProposal().getValue());
	}

	public long getRoundsPerEpoch() throws Exception {
		var response = this.engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
			.networkIdentifier(networkIdentifier())
		);
		return response.getForks().get(0).getEngineConfiguration().getMaximumRoundsPerEpoch();
	}

	public long unstakingDelayEpochLength() {
		try {
			return engineConfigurationHandler.handleRequest(new EngineConfigurationRequest()
				.networkIdentifier(new NetworkIdentifier().network("localnet"))
			).getForks().get(0).getEngineConfiguration().getUnstakingDelayEpochLength();
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}
	}

	public EntityResponse getEntity(EntityIdentifier entityIdentifier) {
		try {
			var response = entityHandler.handleRequest(new EntityRequest()
				.networkIdentifier(new NetworkIdentifier().network("localnet"))
				.entityIdentifier(entityIdentifier)
			);
			return response;
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}
	}

	public List<ResourceAmount> getUnstakes(REAddr addr, ECPublicKey validatorKey) {
		var networkIdentifier = new NetworkIdentifier().network("localnet");
		var unstakingDelayEpochLength = unstakingDelayEpochLength();
		var unstakes = new ArrayList<ResourceAmount>();
		try {
			var statusResponse = engineStatusHandler
				.handleRequest(new EngineStatusRequest().networkIdentifier(networkIdentifier));
			var curEpoch = statusResponse.getEngineStateIdentifier().getEpoch();
			var maxEpoch = curEpoch + unstakingDelayEpochLength + 1;

			for (long epochUnstake = curEpoch; epochUnstake <= maxEpoch; epochUnstake++) {
				var response = entityHandler.handleRequest(new EntityRequest()
					.networkIdentifier(networkIdentifier)
					.entityIdentifier(coreModelMapper.entityIdentifierExitingStake(addr, validatorKey, epochUnstake))
				);
				unstakes.addAll(response.getBalances());
			}
		} catch (CoreApiException e) {
			throw new IllegalStateException(e);
		}

		return unstakes;
	}

	public List<CommittedTransaction> getTransactions(long stateVersion, long limit) throws Exception {
		var response = transactionsHandler.handleRequest(new CommittedTransactionsRequest()
			.networkIdentifier(networkIdentifier())
			.stateIdentifier(new PartialStateIdentifier().stateVersion(stateVersion))
			.limit(limit)
		);
		return response.getTransactions();
	}

	public void submit(NodeTransactionAction action) throws Exception {
		var networkIdentifier = networkIdentifier();
		var engineConfigurationResponse = engineConfigurationHandler.handleRequest(
			new EngineConfigurationRequest().networkIdentifier(networkIdentifier)
		);
		var nodeIdentifiersResponse = nodeIdentifiersHandler.handleRequest(new NodeIdentifiersRequest().networkIdentifier(networkIdentifier));
		var nodeIdentifiers = nodeIdentifiersResponse.getNodeIdentifiers();
		var configuration = engineConfigurationResponse.getForks().get(0).getEngineConfiguration();
		var operationGroup = action.toOperationGroup(configuration, nodeIdentifiers);

		try {
			var buildResponse = constructionBuildHandler.handleRequest(new ConstructionBuildRequest()
				.networkIdentifier(networkIdentifier)
				.feePayer(nodeIdentifiers.getAccountEntityIdentifier())
				.operationGroups(List.of(operationGroup))
			);
			var unsignedTransaction = buildResponse.getUnsignedTransaction();

			var response = nodeSignHandler.handleRequest(new NodeSignRequest()
				.networkIdentifier(networkIdentifier)
				.publicKey(nodeIdentifiers.getPublicKey())
				.unsignedTransaction(unsignedTransaction)
			);

			constructionSubmitHandler.handleRequest(new ConstructionSubmitRequest()
				.networkIdentifier(networkIdentifier)
				.signedTransaction(response.getSignedTransaction())
			);
		} catch (CoreApiException e) {
			var okayErrors = Set.of(
				MempoolFullError.class,
				NotEnoughResourcesError.class,
				AboveMaximumValidatorFeeIncreaseError.class,
				BelowMinimumStakeError.class,
				NotValidatorOwnerError.class
			);

			// Throw error if not expected
			if (!okayErrors.contains(e.toError().getDetails().getClass())) {
				throw e;
			}
		}
	}
}
