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
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorMetaData;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.store.EngineStore;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.utils.Pair;
import org.bouncycastle.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
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
	private final EngineStore<LedgerAndBFTProof> engineStore;
	private final ECPublicKey bftKey;
	private final Addressing addressing;
	private final ValidatorInfoService validatorInfoService;
	private final Forks forks;
	private final InMemorySystemInfo inMemorySystemInfo;

	@Inject
	public AccountInfoService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EngineStore<LedgerAndBFTProof> engineStore,
		@Self ECPublicKey bftKey,
		Addressing addressing,
		Forks forks,
		InMemorySystemInfo inMemorySystemInfo, // TODO: This is a hack, remove
		ValidatorInfoService validatorInfoService
	) {
		this.radixEngine = radixEngine;
		this.engineStore = engineStore;
		this.bftKey = bftKey;
		this.addressing = addressing;
		this.forks = forks;
		this.validatorInfoService = validatorInfoService;
		this.inMemorySystemInfo = inMemorySystemInfo;
	}

	private SubstateDeserialization retrieveEpochParser() {
		return forks.get(inMemorySystemInfo.getCurrentProof().getEpoch())
			.getParser()
			.getSubstateDeserialization();
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
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_ALLOW_DELEGATION_FLAG.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (AllowDelegationFlag) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(AllowDelegationFlag.createVirtual(bftKey));
	}

	public ValidatorRegisteredCopy getMyNextEpochRegisteredFlag() {
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_REGISTERED_FLAG_COPY.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (ValidatorRegisteredCopy) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(ValidatorRegisteredCopy.createVirtual(bftKey));
	}

	public ValidatorRakeCopy getMyNextValidatorFee() {
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_RAKE_COPY.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (ValidatorRakeCopy) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(ValidatorRakeCopy.createVirtual(bftKey));
	}

	public ValidatorOwnerCopy getMyNextEpochValidatorOwner() {
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_OWNER_COPY.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (ValidatorOwnerCopy) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(ValidatorOwnerCopy.createVirtual(bftKey));
	}

	public ValidatorMetaData getMyValidatorMetadata() {
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_META_DATA.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (ValidatorMetaData) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(ValidatorMetaData.createVirtual(bftKey));
	}

	public UInt256 getTotalStake() {
		return getMyValidator().getTotalStake();
	}

	public ValidatorStakeData getMyValidator() {
		var deserializer = retrieveEpochParser();
		var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_STAKE_DATA.id(), bftKey.getCompressedBytes());
		return engineStore.get(validatorDataKey)
			.map(raw -> {
				try {
					return (ValidatorStakeData) deserializer.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			})
			.orElse(ValidatorStakeData.createVirtual(bftKey));
	}

	private Pair<ValidatorStakeData, JSONArray> getStakeData() {
		var deserializer = retrieveEpochParser();
		var myValidator = getMyValidator();

		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[] {SubstateTypeId.STAKE_OWNERSHIP.id(), 0}, bftKey.getCompressedBytes()),
			StakeOwnership.class
		);
		final Map<REAddr, UInt384> stakeReceived = new HashMap<>();
		try (var cursor = engineStore.openIndexedCursor(index)) {
			while (cursor.hasNext()) {
				try {
					var ownership = (StakeOwnership) deserializer.deserialize(cursor.next().getData());
					stakeReceived.merge(ownership.getOwner(), UInt384.from(ownership.getAmount()), UInt384::add);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}

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
		final Map<REAddr, UInt384> balances = new HashMap<>();
		var deserializer = retrieveEpochParser();
		try (var cursor = engineStore.openIndexedCursor(index)) {
			while (cursor.hasNext()) {
				try {
					var tokens = (TokensInAccount) deserializer.deserialize(cursor.next().getData());
					balances.merge(tokens.getResourceAddr(), UInt384.from(tokens.getAmount()), UInt384::add);
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}

		return balances;
	}

	public Map<ECPublicKey, UInt384> getMyPreparedStakes() {
		var deserializer = retrieveEpochParser();

		var preparedStakeIndex = SubstateIndex.create(SubstateTypeId.PREPARED_STAKE.id(), PreparedStake.class);
		final Map<ECPublicKey, UInt384> preparedStakes = new HashMap<>();
		try (var cursor = engineStore.openIndexedCursor(preparedStakeIndex)) {
			while (cursor.hasNext()) {
				try {
					var preparedStake = (PreparedStake) deserializer.deserialize(cursor.next().getData());
					if (preparedStake.getOwner().equals(REAddr.ofPubKeyAccount(bftKey))) {
						preparedStakes.merge(preparedStake.getDelegateKey(), UInt384.from(preparedStake.getAmount()), UInt384::add);
					}
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}

		return preparedStakes;
	}

	public Map<ECPublicKey, UInt384> getMyStakeBalances() {
		var deserializer = retrieveEpochParser();

		var index = SubstateIndex.create(SubstateTypeId.STAKE_OWNERSHIP.id(), TokensInAccount.class);
		final Map<ECPublicKey, UInt384> stakeOwnerships = new HashMap<>();
		try (var cursor = engineStore.openIndexedCursor(index)) {
			while (cursor.hasNext()) {
				try {
					var stakeOwnership = (StakeOwnership) deserializer.deserialize(cursor.next().getData());
					if (stakeOwnership.getOwner().equals(REAddr.ofPubKeyAccount(bftKey))) {
						stakeOwnerships.merge(stakeOwnership.getDelegateKey(), UInt384.from(stakeOwnership.getAmount()), UInt384::add);
					}
				} catch (DeserializeException e) {
					throw new IllegalStateException();
				}
			}
		}

		final Map<ECPublicKey, UInt384> stakes = new HashMap<>();
		for (var e : stakeOwnerships.entrySet()) {
			var validatorDataKey = SystemMapKey.ofValidatorData(SubstateTypeId.VALIDATOR_STAKE_DATA.id(), e.getKey().getCompressedBytes());
			var raw = engineStore.get(validatorDataKey).orElseThrow();
			try {
				var validatorData = (ValidatorStakeData) deserializer.deserialize(raw.getData());
				var stake = e.getValue().multiply(validatorData.getTotalStake()).divide(validatorData.getTotalOwnership());
				stakes.put(e.getKey(), stake);
			} catch (DeserializeException ex) {
				throw new IllegalStateException(ex);
			}
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
			.put("prepared_stakes", preparedStakesArray)
			.put("stakes", stakesArray);
	}

	private JSONObject constructBalanceEntry(REAddr resourceAddress, UInt384 amount) {
		return jsonObject().put("resource_address", resourceAddress.toString()).put("amount", amount);
	}

	private JSONObject constructStakeEntry(ECPublicKey publicKey, UInt384 amount) {
		return jsonObject().put("delegate", addressing.forValidators().of(publicKey)).put("amount", amount);
	}
}
