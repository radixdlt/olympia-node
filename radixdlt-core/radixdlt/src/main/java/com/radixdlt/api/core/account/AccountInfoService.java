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

package com.radixdlt.api.core.account;

import org.bouncycastle.util.Arrays;
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.functional.FunctionalRadixEngine;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.util.HashMap;
import java.util.Map;

import static com.radixdlt.api.util.JsonRpcUtil.fromMap;
import static com.radixdlt.api.util.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.util.StakeUtils.toFullAmount;
import static com.radixdlt.atom.SubstateTypeId.PREPARED_STAKE;
import static com.radixdlt.atom.SubstateTypeId.STAKE_OWNERSHIP;
import static com.radixdlt.atom.SubstateTypeId.TOKENS;
import static com.radixdlt.atom.SubstateTypeId.TOKEN_RESOURCE_METADATA;
import static com.radixdlt.atom.SubstateTypeId.VALIDATOR_STAKE_DATA;
import static com.radixdlt.utils.functional.Result.allOf;

public class AccountInfoService {
	private final FunctionalRadixEngine radixEngine;
	private final ECPublicKey bftKey;
	private final Addressing addressing;

	@Inject
	public AccountInfoService(
		FunctionalRadixEngine radixEngine,
		@Self ECPublicKey bftKey,
		Addressing addressing
	) {
		this.radixEngine = radixEngine;
		this.bftKey = bftKey;
		this.addressing = addressing;
	}

	public Result<JSONObject> getAccountInfo() {
		return allOf(Result.ok(getOwnAddress()), getOwnBalance())
			.map((ownerAddress, ownBalance) -> jsonObject()
				.put("address", ownerAddress)
				.put("balance", ownBalance));
	}

	private String getOwnAddress() {
		return addressing.forAccounts().of(REAddr.ofPubKeyAccount(bftKey));
	}

	public Result<Map<REAddr, UInt384>> getMyBalances() {
		var index = SubstateIndex.create(
			Arrays.concatenate(new byte[]{TOKENS.id(), 0}, REAddr.ofPubKeyAccount(bftKey).getBytes()),
			TokensInAccount.class
		);

		return radixEngine.reduceResources(index, TokensInAccount::getResourceAddr);
	}

	public Result<Map<ECPublicKey, UInt384>> getMyPreparedStakes() {
		var index = SubstateIndex.create(PREPARED_STAKE.id(), PreparedStake.class);

		return radixEngine.reduceResources(index, PreparedStake::getDelegateKey, p -> p.getOwner().equals(REAddr.ofPubKeyAccount(bftKey)));
	}

	public Result<Map<ECPublicKey, UInt384>> getMyStakeBalances() {
		var index = SubstateIndex.create(STAKE_OWNERSHIP.id(), StakeOwnership.class);

		return radixEngine.reduceResources(
			index,
			StakeOwnership::getDelegateKey,
			p -> p.getOwner().equals(REAddr.ofPubKeyAccount(bftKey))
		).map(stakeOwnerships -> {
			var stakes = new HashMap<ECPublicKey, UInt384>();

			stakeOwnerships.forEach((addr, amount) -> {
				var validatorDataKey = SystemMapKey.ofSystem(VALIDATOR_STAKE_DATA.id(), addr.getCompressedBytes());

				radixEngine.getParticle(validatorDataKey)
					.map(ValidatorStakeData.class::cast)
					.onSuccess(validatorData -> stakes.put(addr, toFullAmount(validatorData, amount)));
			});
			return stakes;
		});
	}

	private Result<Map<REAddr, Pair<String, UInt384>>> withSymbol(Result<Map<REAddr, UInt384>> myBalances) {
		return myBalances.map(balances -> {
			var pairs = new HashMap<REAddr, Pair<String, UInt384>>();

			balances.forEach((addr, amount) -> {
				var mapKey = SystemMapKey.ofResourceData(addr, TOKEN_RESOURCE_METADATA.id());

				radixEngine.getParticle(mapKey)
					.map(TokenResourceMetadata.class::cast)
					.onSuccess(metadata -> pairs.put(addr, Pair.of(metadata.getSymbol(), amount)));
			});

			return pairs;
		});
	}

	private Result<JSONObject> getOwnBalance() {
		return allOf(withSymbol(getMyBalances()), getMyPreparedStakes(), getMyStakeBalances())
			.map((myBalances, myPreparedStakes, myStakeBalances) ->
					 jsonObject()
						 .put("tokens", fromMap(myBalances, this::constructBalanceEntry))
						 .put("preparedStakes", fromMap(myPreparedStakes, this::constructStakeEntry))
						 .put("stakes", fromMap(myStakeBalances, this::constructStakeEntry)));
	}

	private JSONObject constructBalanceEntry(REAddr resourceAddress, Pair<String, UInt384> symbolWithAmount) {
		return jsonObject()
			.put("rri", addressing.forResources().of(symbolWithAmount.getFirst(), resourceAddress))
			.put("amount", symbolWithAmount.getSecond());
	}

	private JSONObject constructStakeEntry(ECPublicKey publicKey, UInt384 amount) {
		return jsonObject()
			.put("delegate", addressing.forValidators().of(publicKey))
			.put("amount", amount);
	}
}
