package com.radix.regression.doublespend;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionsState;
import com.radixdlt.client.application.translate.tokens.TokenState;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.radix.common.tuples.Pair;

public class DoubleSpendCreateTokenTestConditions implements DoubleSpendTestConditions {
	private final RadixAddress apiAddress;
	private final TokenDefinitionReference tokenRef;

	DoubleSpendCreateTokenTestConditions(RadixAddress apiAddress) {
		this.tokenRef = TokenDefinitionReference.of(apiAddress, "JOSH");
		this.apiAddress = apiAddress;
	}

	@Override
	public List<Action> initialActions() {
		return Collections.emptyList();
	}

	@Override
	public List<List<Action>> conflictingActions() {
		return Arrays.asList(
			Collections.singletonList(
				CreateTokenAction.create(
					apiAddress,
					"Joshy Token",
					"JOSH",
					"Cool Token",
					BigDecimal.ONE,
					BigDecimal.ONE,
					TokenSupplyType.MUTABLE
				)
			),
			Collections.singletonList(
				CreateTokenAction.create(
					apiAddress,
					"Joshy Token 2",
					"JOSH",
					"Cool Token 2",
					BigDecimal.valueOf(2),
					BigDecimal.ONE,
					TokenSupplyType.FIXED
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
				return  (
					tokenState.getTotalSupply().compareTo(BigDecimal.valueOf(2)) == 0
					&& tokenState.getName().endsWith("2")
					&& tokenState.getDescription().endsWith("2")
				) || (
					tokenState.getTotalSupply().compareTo(BigDecimal.ONE) == 0
					&& !tokenState.getName().endsWith("2")
					&& !tokenState.getDescription().endsWith("2")
				);
			}, "TokenDef JOSH either of type 1 or type 2")
		);
	}
}
