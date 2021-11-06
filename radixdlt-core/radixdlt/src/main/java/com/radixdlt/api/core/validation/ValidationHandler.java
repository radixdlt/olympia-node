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

package com.radixdlt.api.core.validation;

import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.data.ValidatorUptime;
import com.radixdlt.api.service.ValidatorInfoService;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.functional.Result;

import static com.radixdlt.api.util.JsonRpcUtil.fromMap;
import static com.radixdlt.api.util.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.util.JsonRpcUtil.successResponse;
import static com.radixdlt.api.util.JsonRpcUtil.withoutParameters;
import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_PERCENTAGE_GRANULARITY;
import static com.radixdlt.utils.functional.Result.allOf;

public final class ValidationHandler {
	private final ValidatorInfoService validatorInfoService;
	private final Addressing addressing;
	private final ECPublicKey self;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public ValidationHandler(
		ValidatorInfoService validatorInfoService,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@Self ECPublicKey self,
		Addressing addressing
	) {
		this.validatorInfoService = validatorInfoService;
		this.radixEngine = radixEngine;
		this.self = self;
		this.addressing = addressing;
	}

	private Result<JSONObject> getValidatorInfo(
		ValidatorMetaData metadata,
		ValidatorStakeData validatorData,
		AllowDelegationFlag allowDelegationFlag,
		ValidatorUptime uptime
	) {
		return allOf(
			validatorInfoService.getEstimatedIndividualStakes(validatorData),
			validatorInfoService.getNextEpochRegisteredFlag(self),
			validatorInfoService.getNextEpochValidatorOwner(self),
			validatorInfoService.getPreparedStakes(self),
			validatorInfoService.getEstimatedPreparedUnstakes(validatorData)
		).map((individualStakes, nextEpochRegisteredFlag, nextEpochOwner, preparedStakes, preparedUnstakes) -> {
			var validatorAddress = addressing.forValidators().of(self);

			var data = jsonObject()
				.put("name", metadata.getName())
				.put("url", metadata.getUrl())
				.put("address", validatorAddress)
				.put("allowDelegation", allowDelegationFlag.allowsDelegation());

			var stakesJson = fromMap(individualStakes, (addr, amt) -> jsonObject()
				.put("delegator", addressing.forAccounts().of(addr))
				.put("amount", amt)
			);

			var curEpoch = jsonObject()
				.put("registered", validatorData.isRegistered())
				.put("owner", addressing.forAccounts().of(validatorData.getOwnerAddr()))
				.put("validatorFee", toPercentageString(validatorData.getRakePercentage()))
				.put("stakes", stakesJson)
				.put("totalStake", validatorData.getTotalStake())
				.put("proposalsCompleted", uptime.getProposalsCompleted())
				.put("proposalsMissed", uptime.getProposalsMissed())
				.put("uptimePercentage", uptime.toPercentageString());

			var updates = jsonObject();

			validatorInfoService.getNextValidatorFee(self)
				.onSuccess(validatorRakeCopy -> {
					if (validatorRakeCopy.getRakePercentage() != validatorData.getRakePercentage()) {
						updates.put("validatorFee", toPercentageString(validatorRakeCopy.getRakePercentage()));
					}
				});

			if (nextEpochRegisteredFlag.isRegistered() != validatorData.isRegistered()) {
				updates.put("registered", nextEpochRegisteredFlag);
			}

			if (!nextEpochOwner.getOwner().equals(validatorData.getOwnerAddr())) {
				updates.put("owner", addressing.forAccounts().of(nextEpochOwner.getOwner()));
			}

			if (!preparedStakes.isEmpty()) {
				updates.put("stakes", fromMap(
					preparedStakes,
					(addr, amount) -> jsonObject()
						.put("amount", amount)
						.put("delegator", addressing.forAccounts().of(addr))
				));
			}

			if (!preparedUnstakes.isEmpty()) {
				updates.put("unstakes", fromMap(
					preparedUnstakes, (addr, amount) -> jsonObject()
						.put("amount", amount)
						.put("delegator", addressing.forAccounts().of(addr))
				));
			}

			return data.put("epochInfo", jsonObject()
				.put("current", curEpoch)
				.put("updates", updates)
			);
		});
	}

	private static String toPercentageString(double percentage) {
		return percentage / (double) RAKE_PERCENTAGE_GRANULARITY + "";
	}

	public JSONObject handleGetNodeInfo(JSONObject request) {
		return withoutParameters(
			request,
			() -> allOf(
				validatorInfoService.getMetadata(self),
				validatorInfoService.getValidatorStakeData(self),
				validatorInfoService.getAllowDelegationFlag(self),
				validatorInfoService.getUptime(self)
			).flatMap(this::getValidatorInfo)
		);
	}

	public JSONObject handleGetCurrentEpochData(JSONObject request) {
		//TODO: rework to avoid throwing exceptions
		var validators = radixEngine.reduce(ValidatorStakeData.class, jsonArray(), (json, data) -> {
			if (!data.isRegistered() || data.getTotalStake().isZero()) {
				return json;
			}

			var bftDataKey = SystemMapKey.ofSystem(
				SubstateTypeId.VALIDATOR_BFT_DATA.id(),
				data.getValidatorKey().getCompressedBytes()
			);

			//TODO: need to surround this with a lock
			//TODO: may throw unexpected exception
			var bftData = (ValidatorBFTData) radixEngine.get(bftDataKey).orElseThrow();
			var uptime = ValidatorUptime.create(bftData.proposalsCompleted(), bftData.proposalsMissed());

			var validatorJson = jsonObject()
				.put("address", addressing.forValidators().of(data.getValidatorKey()))
				.put("totalDelegatedStake", data.getTotalStake())
				.put("proposalsCompleted", bftData.proposalsCompleted())
				.put("proposalsMissed", bftData.proposalsMissed())
				.put("uptimePercentage", uptime.toPercentageString());

			return json.put(validatorJson);
		}, 100); // TODO: remove the 100 hardcode

		return successResponse(request, jsonObject().put("validators", validators));
	}
}
