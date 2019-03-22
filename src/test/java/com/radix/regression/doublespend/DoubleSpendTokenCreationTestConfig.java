package com.radix.regression.doublespend;

import com.radixdlt.client.application.translate.Action;
import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction;
import com.radixdlt.client.application.translate.tokens.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.MintTokensAction;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.Condition;
import org.radix.common.tuples.Pair;

public class DoubleSpendTokenCreationTestConfig implements DoubleSpendTestConfig {
	private final RadixAddress apiAddress;
	private final TokenDefinitionReference tokenRef;

	DoubleSpendTokenCreationTestConfig(RadixAddress apiAddress) {
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
			Arrays.asList(
				CreateTokenAction.create(
					apiAddress,
					"Joshy Token",
					"JOSH",
					"Cool Token",
					BigDecimal.ZERO,
					BigDecimal.ONE,
					TokenSupplyType.MUTABLE
				),
				MintTokensAction.create(
					tokenRef,
					BigDecimal.ONE
				)
			),
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
				return balanceState.getBalance().get(tokenRef).getAmount().compareTo(BigDecimal.ONE) == 0;
			}, "1 JOSH in account")
		);
	}
}
