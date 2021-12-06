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

package com.radixdlt.integration.staking;

import com.radixdlt.api.core.core.CoreApiException;
import com.radixdlt.api.core.core.CoreModelMapper;
import com.radixdlt.api.core.core.handlers.ConstructionBuildHandler;
import com.radixdlt.api.core.core.handlers.ConstructionSubmitHandler;
import com.radixdlt.api.core.core.handlers.NetworkConfigurationHandler;
import com.radixdlt.api.core.core.handlers.NetworkStatusHandler;
import com.radixdlt.api.core.core.handlers.SignHandler;
import com.radixdlt.api.core.core.openapitools.model.AboveMaximumValidatorFeeIncreaseError;
import com.radixdlt.api.core.core.openapitools.model.BelowMinimumStakeError;
import com.radixdlt.api.core.core.openapitools.model.ConstructionBuildRequest;
import com.radixdlt.api.core.core.openapitools.model.ConstructionSubmitRequest;
import com.radixdlt.api.core.core.openapitools.model.Data;
import com.radixdlt.api.core.core.openapitools.model.EntityIdentifier;
import com.radixdlt.api.core.core.openapitools.model.MempoolFullError;
import com.radixdlt.api.core.core.openapitools.model.NetworkIdentifier;
import com.radixdlt.api.core.core.openapitools.model.NetworkStatusRequest;
import com.radixdlt.api.core.core.openapitools.model.NotEnoughResourcesError;
import com.radixdlt.api.core.core.openapitools.model.NotValidatorOwnerError;
import com.radixdlt.api.core.core.openapitools.model.Operation;
import com.radixdlt.api.core.core.openapitools.model.OperationGroup;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorFee;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorOwner;
import com.radixdlt.api.core.core.openapitools.model.PreparedValidatorRegistered;
import com.radixdlt.api.core.core.openapitools.model.SignRequest;
import com.radixdlt.api.core.core.openapitools.model.SubEntity;
import com.radixdlt.api.core.core.openapitools.model.SubEntityMetadata;
import com.radixdlt.api.core.core.openapitools.model.ValidatorAllowDelegation;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt;
import com.radixdlt.environment.deterministic.MultiNodeDeterministicRunner;
import com.radixdlt.identifiers.REAddr;

import java.util.List;
import java.util.Random;
import java.util.Set;

public final class RandomTransactionSubmitter implements DeterministicActionExecutor {
	private final MultiNodeDeterministicRunner multiNodeDeterministicRunner;
	private final Random random;

	public RandomTransactionSubmitter(MultiNodeDeterministicRunner multiNodeDeterministicRunner, Random random) {
		this.multiNodeDeterministicRunner = multiNodeDeterministicRunner;
		this.random = random;
	}

	@Override
	public void execute() throws Exception {
		int size = this.multiNodeDeterministicRunner.getSize();
		var nodeIndex = this.random.nextInt(size);
		var nodeInjector = this.multiNodeDeterministicRunner.getNode(nodeIndex);
		var networkConfigurationHandler = nodeInjector.getInstance(NetworkConfigurationHandler.class);
		var networkStatusHandler = nodeInjector.getInstance(NetworkStatusHandler.class);
		var buildHandler = nodeInjector.getInstance(ConstructionBuildHandler.class);
		var signHandler = nodeInjector.getInstance(SignHandler.class);
		var submitHandler = nodeInjector.getInstance(ConstructionSubmitHandler.class);
		var coreModelMapper = nodeInjector.getInstance(CoreModelMapper.class);

		var networkResponse = networkConfigurationHandler.handleRequest((Void) null);
		var networkIdentifier = networkResponse.getNetworkIdentifier();
		var networkStatusResponse = networkStatusHandler.handleRequest(new NetworkStatusRequest().networkIdentifier(networkIdentifier));
		var nodeIdentifiers = networkStatusResponse.getNodeIdentifiers();

		var otherNodeIndex = this.random.nextInt(size);
		var otherNodeInjector = this.multiNodeDeterministicRunner.getNode(otherNodeIndex);
		var otherNodeStatusHandler = otherNodeInjector.getInstance(NetworkStatusHandler.class);
		var otherNodeStatusResponse = otherNodeStatusHandler.handleRequest(new NetworkStatusRequest().networkIdentifier(networkIdentifier));
		var otherNodeIdentifiers = otherNodeStatusResponse.getNodeIdentifiers();

		var next = random.nextInt(8);
		var amount = Amount.ofTokens(random.nextInt(10) * 10 + 1).toSubunits();

		if (next == 4 && nodeIndex <= 0) {
			return;
		}

		final OperationGroup operationGroup = switch (next) {
			case 0 -> new OperationGroup().operations(List.of(
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.nativeTokenAmount(false, amount))
					.entityIdentifier(nodeIdentifiers.getAccountEntityIdentifier()),
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.nativeTokenAmount(true, amount))
					.entityIdentifier(otherNodeIdentifiers.getAccountEntityIdentifier())
			));
			case 1 -> new OperationGroup().operations(List.of(
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.nativeTokenAmount(false, amount))
					.entityIdentifier(nodeIdentifiers.getAccountEntityIdentifier()),
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.nativeTokenAmount(true, amount))
					.entityIdentifier(
						new EntityIdentifier()
							.address(nodeIdentifiers.getAccountEntityIdentifier().getAddress())
							.subEntity(new SubEntity()
								.address("prepared_stake")
								.metadata(new SubEntityMetadata().validatorAddress(otherNodeIdentifiers.getValidatorEntityIdentifier().getAddress()))
							)
					)
			));
			case 2 -> new OperationGroup().operations(List.of(
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.stakeUnitAmount(false, otherNodeIdentifiers.getValidatorEntityIdentifier().getAddress(), amount))
					.entityIdentifier(nodeIdentifiers.getAccountEntityIdentifier()),
				new Operation()
					.type("Resource")
					.amount(coreModelMapper.stakeUnitAmount(true, otherNodeIdentifiers.getValidatorEntityIdentifier().getAddress(), amount))
					.entityIdentifier(
						new EntityIdentifier()
							.address(nodeIdentifiers.getAccountEntityIdentifier().getAddress())
							.subEntity(new SubEntity().address("prepared_unstake"))
					)
			));
			case 3 -> new OperationGroup().addOperationsItem(
				new Operation()
					.type("Data")
					.data(new Data().action(Data.ActionEnum.CREATE)
						.dataObject(new PreparedValidatorRegistered().registered(true).type(PreparedValidatorRegistered.class.getSimpleName()))
					)
					.entityIdentifier(nodeIdentifiers.getValidatorEntityIdentifier())
			);
			case 4 -> new OperationGroup().addOperationsItem(
				new Operation()
					.type("Data")
					.data(new Data().action(Data.ActionEnum.CREATE)
						.dataObject(new PreparedValidatorRegistered().registered(false).type(PreparedValidatorRegistered.class.getSimpleName()))
					)
					.entityIdentifier(nodeIdentifiers.getValidatorEntityIdentifier())
			);
			case 5 -> new OperationGroup().addOperationsItem(
				new Operation()
					.type("Data")
					.data(new Data().action(Data.ActionEnum.CREATE)
						.dataObject(new PreparedValidatorFee()
							.fee(random.nextInt(ValidatorUpdateRakeConstraintScrypt.RAKE_MAX + 1))
							.type(PreparedValidatorFee.class.getSimpleName())
						)
					)
					.entityIdentifier(nodeIdentifiers.getValidatorEntityIdentifier())
			);
			case 6 -> new OperationGroup().addOperationsItem(
				new Operation()
					.type("Data")
					.data(new Data().action(Data.ActionEnum.CREATE)
						.dataObject(new PreparedValidatorOwner()
							.owner(otherNodeIdentifiers.getAccountEntityIdentifier())
							.type(PreparedValidatorOwner.class.getSimpleName())
						)
					)
					.entityIdentifier(nodeIdentifiers.getValidatorEntityIdentifier())
			);
			case 7 -> new OperationGroup().addOperationsItem(
				new Operation()
					.type("Data")
					.data(new Data().action(Data.ActionEnum.CREATE)
						.dataObject(new ValidatorAllowDelegation()
							.allowDelegation(random.nextBoolean())
							.type(ValidatorAllowDelegation.class.getSimpleName())
						)
					)
					.entityIdentifier(nodeIdentifiers.getValidatorEntityIdentifier())
			);
			default -> throw new IllegalStateException("Unexpected value: " + next);
		};

		try {
			var buildResponse = buildHandler.handleRequest(new ConstructionBuildRequest()
				.networkIdentifier(networkIdentifier)
				.feePayer(nodeIdentifiers.getAccountEntityIdentifier())
				.operationGroups(List.of(operationGroup))
			);
			var unsignedTransaction = buildResponse.getUnsignedTransaction();

			var response = signHandler.handleRequest(new SignRequest()
				.networkIdentifier(networkIdentifier)
				.publicKey(nodeIdentifiers.getPublicKey())
				.unsignedTransaction(unsignedTransaction)
			);

			submitHandler.handleRequest(new ConstructionSubmitRequest()
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
