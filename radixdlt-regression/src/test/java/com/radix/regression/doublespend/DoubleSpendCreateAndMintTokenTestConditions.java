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

package com.radix.regression.doublespend;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Condition;
import com.radixdlt.utils.Pair;

public class DoubleSpendCreateAndMintTokenTestConditions implements DoubleSpendTestConditions {
	private final RadixAddress apiAddress;
	private final RRI tokenRef;

	DoubleSpendCreateAndMintTokenTestConditions(RadixAddress apiAddress) {
		this.tokenRef = RRI.of(apiAddress, "JOSH");
		this.apiAddress = apiAddress;
	}

	@Override
	public List<BatchedActions> initialActions() {
		return Collections.emptyList();
	}

	@Override
	public List<List<BatchedActions>> conflictingActions() {
		return Arrays.asList(
			Arrays.asList(
				new BatchedActions(
					CreateTokenAction.create(
						this.tokenRef,
						"Joshy Token",
						"Cool Token",
						BigDecimal.ZERO,
						BigDecimal.ONE,
						TokenSupplyType.MUTABLE
					))
				,
				new BatchedActions(
					MintTokensAction.create(
						tokenRef,
						apiAddress,
						BigDecimal.ONE
					)
				)
			),
			Collections.singletonList(
				new BatchedActions(
					CreateTokenAction.create(
						this.tokenRef,
						"Joshy Token",
						"Cool Token",
						BigDecimal.ONE,
						BigDecimal.ONE,
						TokenSupplyType.FIXED
					)
				)
			)
		);
	}

	@Override
	public PostConsensusCondition postConsensusCondition() {
		Set<Pair<String, ShardedAppStateId>> stateRequired = new HashSet<>();
		stateRequired.add(Pair.of("Balance", ShardedAppStateId.of(TokenBalanceState.class, apiAddress)));

		return new PostConsensusCondition(
			stateRequired,
			new Condition<>(map -> {
				TokenBalanceState balanceState = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, apiAddress));
				BigDecimal balance = balanceState.getBalance().get(tokenRef);
				System.err.format("balance = %s, state = %s%n", balance, balanceState);
				return  balance != null && balance.compareTo(BigDecimal.ONE) == 0;
			}, "1 JOSH in account")
		);
	}
}
