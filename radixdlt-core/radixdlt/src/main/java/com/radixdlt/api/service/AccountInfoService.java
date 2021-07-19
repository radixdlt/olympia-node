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

package com.radixdlt.api.service;

import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.utils.Pair;
import org.bouncycastle.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.HashMap;
import java.util.Map;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.application.validators.scrypt.ValidatorUpdateRakeConstraintScrypt.RAKE_PERCENTAGE_GRANULARITY;

public class AccountInfoService {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ECPublicKey bftKey;
	private final Addressing addressing;

	@Inject
	public AccountInfoService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@Self ECPublicKey bftKey,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.bftKey = bftKey;
		this.addressing = addressing;
	}

	public JSONObject getAccountInfo() {
		return jsonObject()
			.put("address", getOwnAddress())
			.put("balance", getOwnBalance());
	}

	public JSONObject getValidatorInfo() {
		var metadata = getMyValidatorMetadata();
		var stakeData = getStakeData();
		var allowDelegationFlag = getMyValidatorDelegationFlag();
		var validatorRakeCopy = getMyNextValidatorFee();
		var nextEpochRegisteredFlag = getMyNextEpochRegisteredFlag().isRegistered();
		var nextEpochOwner = getMyNextEpochValidatorOwner().getOwner();
		return jsonObject()
			.put("name", metadata.getName())
			.put("url", metadata.getUrl())
			.put("address", getValidatorAddress())
			.put("registered", nextEpochRegisteredFlag)
			.put("owner", addressing.forAccounts().of(nextEpochOwner))
			.put("validatorFee", (double) validatorRakeCopy.getRakePercentage() / (double) RAKE_PERCENTAGE_GRANULARITY + "")
			.put("totalStake", stakeData.getFirst().getTotalStake())
			.put("allowDelegation", allowDelegationFlag.allowsDelegation())
			.put("stakes", stakeData.getSecond());
	}

	public String getValidatorAddress() {
		return addressing.forValidators().of(bftKey);
	}

	public AllowDelegationFlag getMyValidatorDelegationFlag() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG.id(), bftKey.getCompressedBytes());
		return (AllowDelegationFlag) radixEngine.get(validatorDataKey).orElse(AllowDelegationFlag.createVirtual(bftKey));
	}

	public ValidatorRegisteredCopy getMyNextEpochRegisteredFlag() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(), bftKey.getCompressedBytes());
		return (ValidatorRegisteredCopy) radixEngine.get(validatorDataKey).orElse(ValidatorRegisteredCopy.createVirtual(bftKey));
	}

	public ValidatorRakeCopy getMyNextValidatorFee() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_RAKE_COPY.id(), bftKey.getCompressedBytes());
		return (ValidatorRakeCopy) radixEngine.get(validatorDataKey).orElse(ValidatorRakeCopy.createVirtual(bftKey));
	}

	public ValidatorOwnerCopy getMyNextEpochValidatorOwner() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_OWNER_COPY.id(), bftKey.getCompressedBytes());
		return (ValidatorOwnerCopy) radixEngine.get(validatorDataKey).orElse(ValidatorOwnerCopy.createVirtual(bftKey));
	}

	public ValidatorMetaData getMyValidatorMetadata() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_META_DATA.id(), bftKey.getCompressedBytes());
		return (ValidatorMetaData) radixEngine.get(validatorDataKey).orElse(ValidatorMetaData.createVirtual(bftKey));
	}

	public UInt256 getTotalStake() {
		return getMyValidator().getTotalStake();
	}

	public ValidatorStakeData getMyValidator() {
		var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_STAKE_DATA.id(), bftKey.getCompressedBytes());
		return (ValidatorStakeData) radixEngine.get(validatorDataKey).orElse(ValidatorStakeData.createVirtual(bftKey));
	}

	private Pair<ValidatorStakeData, JSONArray> getStakeData() {
		var myValidator = getMyValidator();
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[] {SubstateTypeId.STAKE_OWNERSHIP.id(), 0}, bftKey.getCompressedBytes()),
			StakeOwnership.class
		);
		var stakeReceived = radixEngine.reduceResources(index, StakeOwnership::getOwner);
		var stakeFrom = jsonArray();
		stakeReceived.forEach((address, amt) -> {
			stakeFrom.put(
				jsonObject()
					.put("delegator", addressing.forAccounts().of(address))
					.put("amount", amt.multiply(myValidator.getTotalStake()).divide(myValidator.getTotalOwnership()))
			);
		});
		return Pair.of(myValidator, stakeFrom);
	}

	public String getOwnAddress() {
		return addressing.forAccounts().of(REAddr.ofPubKeyAccount(bftKey));
	}

	public ECPublicKey getOwnPubKey() {
		return bftKey;
	}

	public Map<REAddr, UInt384> getMyBalances() {
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[] {SubstateTypeId.TOKENS.id(), 0}, REAddr.ofPubKeyAccount(bftKey).getBytes()),
			TokensInAccount.class
		);
		return radixEngine.reduceResources(index, TokensInAccount::getResourceAddr);
	}

	public Map<ECPublicKey, UInt384> getMyPreparedStakes() {
		var index = SubstateIndex.create(SubstateTypeId.PREPARED_STAKE.id(), PreparedStake.class);
		return radixEngine.reduceResources(index, PreparedStake::getDelegateKey, p -> p.getOwner().equals(REAddr.ofPubKeyAccount(bftKey)));
	}

	public Map<ECPublicKey, UInt384> getMyStakeBalances() {
		var index = SubstateIndex.create(SubstateTypeId.STAKE_OWNERSHIP.id(), StakeOwnership.class);
		var stakeOwnerships = radixEngine.reduceResources(
			index,
			StakeOwnership::getDelegateKey,
			p -> p.getOwner().equals(REAddr.ofPubKeyAccount(bftKey))
		);

		final Map<ECPublicKey, UInt384> stakes = new HashMap<>();
		for (var e : stakeOwnerships.entrySet()) {
			var validatorDataKey = SystemMapKey.ofSystem(SubstateTypeId.VALIDATOR_STAKE_DATA.id(), e.getKey().getCompressedBytes());
			var validatorData = (ValidatorStakeData) radixEngine.get(validatorDataKey).orElseThrow();
			var stake = e.getValue().multiply(validatorData.getTotalStake()).divide(validatorData.getTotalOwnership());
			stakes.put(e.getKey(), stake);
		}

		return stakes;
	}

	private JSONObject getOwnBalance() {
		var balances = getMyBalances();
		var stakedBalance = getMyStakeBalances();
		var preparedStakes = getMyPreparedStakes();

		var preparedStakesArray = jsonArray();
		preparedStakes.forEach((publicKey, amount) -> preparedStakesArray.put(constructStakeEntry(publicKey, amount)));

		var stakesArray = jsonArray();
		stakedBalance.forEach((publicKey, amount) -> stakesArray.put(constructStakeEntry(publicKey, amount)));

		var balancesArray = jsonArray();
		balances.forEach((rri, amount) -> balancesArray.put(constructBalanceEntry(rri, amount)));

		return jsonObject()
			.put("tokens", balancesArray)
			.put("preparedStakes", preparedStakesArray)
			.put("stakes", stakesArray);
	}

	private JSONObject constructBalanceEntry(REAddr resourceAddress, UInt384 amount) {
		var mapKey = SystemMapKey.ofResourceData(resourceAddress, SubstateTypeId.TOKEN_RESOURCE_METADATA.id());
		var metadata = (TokenResourceMetadata) radixEngine.get(mapKey).orElseThrow();
		var rri = addressing.forResources().of(metadata.getSymbol(), resourceAddress);
		return jsonObject().put("rri", rri).put("amount", amount);
	}

	private JSONObject constructStakeEntry(ECPublicKey publicKey, UInt384 amount) {
		return jsonObject().put("delegate", addressing.forValidators().of(publicKey)).put("amount", amount);
	}
}
