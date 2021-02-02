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
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
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

class DoubleSpendTokenTransferTestConditions implements DoubleSpendTestConditions {
	private final RadixAddress apiAddress;
	private final RadixAddress toAddress;
	private final RRI tokenRef;

	DoubleSpendTokenTransferTestConditions(RadixAddress apiAddress, RadixAddress toAddress) {
		this.tokenRef = RRI.of(apiAddress, "JOSH");
		this.apiAddress = apiAddress;
		this.toAddress = toAddress;
	}

	@Override
	public List<BatchedActions> initialActions() {
		return Collections.singletonList(
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
		);
	}

	@Override
	public List<List<BatchedActions>> conflictingActions() {
		BatchedActions action = new BatchedActions(TransferTokensAction.create(tokenRef, apiAddress, toAddress, BigDecimal.ONE));
		return Arrays.asList(Collections.singletonList(action), Collections.singletonList(action));
	}

	@Override
	public PostConsensusCondition postConsensusCondition() {
		Set<Pair<String, ShardedAppStateId>> stateRequired = new HashSet<>();
		stateRequired.add(Pair.of("Balance 1", ShardedAppStateId.of(TokenBalanceState.class, apiAddress)));
		stateRequired.add(Pair.of("Balance 2", ShardedAppStateId.of(TokenBalanceState.class, toAddress)));

		return new PostConsensusCondition(
			stateRequired,
			new Condition<>(map -> {
				TokenBalanceState tokenBalanceState1 =
					(TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, apiAddress));
				TokenBalanceState tokenBalanceState2 =
					(TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, toAddress));
				return tokenBalanceState1.getBalance().get(tokenRef) == null
					&& tokenBalanceState2.getBalance().get(tokenRef).compareTo(BigDecimal.ONE) == 0;
			}, "Transfer of 1 JOSH from one account to another")
		);
	}
}
