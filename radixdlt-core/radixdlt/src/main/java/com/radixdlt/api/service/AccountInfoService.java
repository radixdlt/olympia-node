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

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.application.MyBalances;
import com.radixdlt.application.MyStakedBalance;
import com.radixdlt.application.MyValidator;
import com.radixdlt.application.MyValidatorInfo;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AccountAddresses;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class AccountInfoService {
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final ECPublicKey bftKey;
	private final AccountAddresses accountAddresses;

	@Inject
	public AccountInfoService(
		RadixEngine<LedgerAndBFTProof> radixEngine,
		@Self ECPublicKey bftKey,
		AccountAddresses accountAddresses
	) {
		this.radixEngine = radixEngine;
		this.bftKey = bftKey;
		this.accountAddresses = accountAddresses;
	}

	public JSONObject getAccountInfo() {
		return jsonObject()
			.put("address", getOwnAddress())
			.put("balance", getOwnBalance());
	}

	public JSONObject getValidatorInfo() {
		var validatorInfo = getValidatorInfoDetails();
		var validatorStakes = getValidatorStakes();

		return new JSONObject()
			.put("address", getValidatorAddress())
			.put("name", validatorInfo.getName())
			.put("url", validatorInfo.getUrl())
			.put("registered", validatorInfo.isRegistered())
			.put("totalStake", validatorStakes.getFirst())
			.put("stakes", validatorStakes.getSecond());
	}

	public String getValidatorAddress() {
		return ValidatorAddress.of(bftKey);
	}

	public MyValidatorInfo getValidatorInfoDetails() {
		return radixEngine.getComputedState(MyValidatorInfo.class);
	}

	public MyValidator getValidatorStakeData() {
		return radixEngine.getComputedState(MyValidator.class);
	}

	private Pair<UInt256, JSONArray> getValidatorStakes() {
		var stakeReceived = getValidatorStakeData();
		var stakeFrom = jsonArray();

		stakeReceived.forEach((address, amt) -> {
			stakeFrom.put(
				jsonObject()
					.put("delegator", accountAddresses.of(address))
					.put("amount", amt)
			);
		});

		return Pair.of(stakeReceived.getTotalStake(), stakeFrom);
	}

	public String getOwnAddress() {
		return accountAddresses.of(REAddr.ofPubKeyAccount(bftKey));
	}

	public ECPublicKey getOwnPubKey() {
		return bftKey;
	}

	public MyBalances getMyBalances() {
		return radixEngine.getComputedState(MyBalances.class);
	}

	private JSONObject getOwnBalance() {
		var balances = getMyBalances();
		var stakedBalance = radixEngine.getComputedState(MyStakedBalance.class);

		var stakesArray = jsonArray();
		stakedBalance.forEach((publicKey, amount) -> stakesArray.put(constructStakeEntry(publicKey, amount)));

		var balancesArray = jsonArray();
		balances.forEach((rri, amount) -> balancesArray.put(constructBalanceEntry(rri, amount)));

		return jsonObject()
			.put("tokens", balancesArray)
			.put("stakes", stakesArray);
	}

	private JSONObject constructBalanceEntry(REAddr rri, UInt384 amount) {
		return jsonObject().put("rri", rri.toString()).put("amount", amount);
	}

	private JSONObject constructStakeEntry(ECPublicKey publicKey, UInt256 amount) {
		return jsonObject().put("delegate", ValidatorAddress.of(publicKey)).put("amount", amount);
	}
}
