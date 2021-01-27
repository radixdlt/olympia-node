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
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenState;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.radixdlt.utils.Pair;
import org.assertj.core.api.Condition;


public class DoubleSpendCreateTokenTestConditions implements DoubleSpendTestConditions {
	private final RadixAddress apiAddress;
	private final RRI tokenRef;

	DoubleSpendCreateTokenTestConditions(RadixAddress apiAddress) {
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
			Collections.singletonList(
				new BatchedActions(
					CreateTokenAction.create(
						this.tokenRef,
						"Joshy Token",
						"Cool Token",
						BigDecimal.ONE,
						BigDecimal.ONE,
						TokenSupplyType.MUTABLE
					)
				)
			),
			Collections.singletonList(
				new BatchedActions(
					CreateTokenAction.create(
						this.tokenRef,
						"Joshy Token 2",
						"Cool Token 2",
						BigDecimal.valueOf(2),
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
		stateRequired.add(Pair.of("TokenDef", ShardedAppStateId.of(TokenDefinitionsState.class, apiAddress)));

		return new PostConsensusCondition(
			stateRequired,
			new Condition<>(map -> {
				TokenDefinitionsState tokenDef = (TokenDefinitionsState) map.get(ShardedAppStateId.of(TokenDefinitionsState.class, apiAddress));
				TokenState tokenState = tokenDef.getState().get(tokenRef);
				if (tokenState == null) {
					return false;
				}
				return  (
					tokenState.getTotalSupply() != null
					&& tokenState.getTotalSupply().compareTo(BigDecimal.valueOf(2)) == 0
					&& tokenState.getName().endsWith("2")
					&& tokenState.getDescription().endsWith("2")
				) || (
					tokenState.getTotalSupply() != null
					&& tokenState.getTotalSupply().compareTo(BigDecimal.ONE) == 0
					&& !tokenState.getName().endsWith("2")
					&& !tokenState.getDescription().endsWith("2")
				);
			}, "TokenDef JOSH either of type 1 or type 2")
		);
	}
}
