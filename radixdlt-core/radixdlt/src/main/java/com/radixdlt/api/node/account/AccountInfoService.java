/*
 * Copyright 2021 Radix DLT Ltd incorporated in England.
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

package com.radixdlt.api.node.account;

import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.engine.RadixEngine;
import org.bouncycastle.util.Arrays;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt384;

import java.util.HashMap;
import java.util.Map;

import static com.radixdlt.api.util.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;

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

	private String getOwnAddress() {
		return addressing.forAccounts().of(REAddr.ofPubKeyAccount(bftKey));
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
