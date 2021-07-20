/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.handler;

import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.service.AccountInfoService;

import static com.radixdlt.api.JsonRpcUtil.*;
import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_PERCENTAGE_GRANULARITY;

public final class ValidationHandler {
	private final AccountInfoService accountService;
	private final Addressing addressing;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;

	@Inject
	public ValidationHandler(
		AccountInfoService accountService,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		Addressing addressing
	) {
		this.accountService = accountService;
		this.radixEngine = radixEngine;
		this.addressing = addressing;
	}

	private JSONObject getValidatorInfo() {
		var metadata = accountService.getMyValidatorMetadata();
		var stakeData = accountService.getStakeData();
		var allowDelegationFlag = accountService.getMyValidatorDelegationFlag();
		var validatorRakeCopy = accountService.getMyNextValidatorFee();
		var nextEpochRegisteredFlag = accountService.getMyNextEpochRegisteredFlag().isRegistered();
		var nextEpochOwner = accountService.getMyNextEpochValidatorOwner().getOwner();
		var uptime = accountService.getMyValidatorUptime();
		return jsonObject()
			.put("name", metadata.getName())
			.put("url", metadata.getUrl())
			.put("address", accountService.getValidatorAddress())
			.put("registered", nextEpochRegisteredFlag)
			.put("owner", addressing.forAccounts().of(nextEpochOwner))
			.put("validatorFee", (double) validatorRakeCopy.getRakePercentage() / (double) RAKE_PERCENTAGE_GRANULARITY + "")
			.put("totalStake", stakeData.getFirst().getTotalStake())
			.put("allowDelegation", allowDelegationFlag.allowsDelegation())
			.put("stakes", stakeData.getSecond())
			.put("proposalsCompleted", uptime.getProposalsCompleted())
			.put("proposalsMissed", uptime.getProposalsMissed())
			.put("uptimePercentage", uptime.toPercentageString());
	}

	public JSONObject handleGetNodeInfo(JSONObject request) {
		return response(request, getValidatorInfo());
	}

	public JSONObject handleGetNextEpochData(JSONObject request) {
		// TODO
		return jsonObject();
	}

	public JSONObject handleGetCurrentEpochData(JSONObject request) {
		var validators = radixEngine.reduce(ValidatorStakeData.class, jsonArray(), (json, data) -> {
			if (!data.isRegistered()) {
				return json;
			}

			var bftDataKey = SystemMapKey.ofSystem(
				SubstateTypeId.VALIDATOR_BFT_DATA.id(),
				data.getValidatorKey().getCompressedBytes()
			);

			// TODO: need to surround this with a lock
			var bftData = (ValidatorBFTData) radixEngine.get(bftDataKey).orElseThrow();

			var validatorJson = jsonObject()
				.put("address", addressing.forValidators().of(data.getValidatorKey()))
				.put("totalDelegatedStake", data.getTotalStake())
				.put("proposalsCompleted", bftData.proposalsCompleted())
				.put("proposalsMissed", bftData.proposalsMissed());

			return json.put(validatorJson);
		}, 100); // TODO: remove the 100 hardcode

		return response(request, jsonObject().put("validators", validators));
	}
}
