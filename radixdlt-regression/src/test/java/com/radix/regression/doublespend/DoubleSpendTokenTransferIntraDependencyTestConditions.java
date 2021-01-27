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

public class DoubleSpendTokenTransferIntraDependencyTestConditions implements DoubleSpendTestConditions {
	private final RadixAddress apiAddress;
	private final RadixAddress toAddress;
	private final RRI tokenRef;

	DoubleSpendTokenTransferIntraDependencyTestConditions(RadixAddress apiAddress, RadixAddress toAddress) {
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
					BigDecimal.valueOf(2),
					BigDecimal.ONE,
					TokenSupplyType.FIXED
				)
			)
		);
	}

	@Override
	public List<List<BatchedActions>> conflictingActions() {
		return Arrays.asList(
			Arrays.asList(
				new BatchedActions(TransferTokensAction.create(tokenRef, apiAddress, toAddress, BigDecimal.ONE)),
				new BatchedActions(TransferTokensAction.create(tokenRef, apiAddress, toAddress, BigDecimal.ONE))
			),
			Arrays.asList(
				new BatchedActions(TransferTokensAction.create(tokenRef, apiAddress, toAddress, BigDecimal.ONE)),
				new BatchedActions(TransferTokensAction.create(tokenRef, apiAddress, toAddress, BigDecimal.ONE))
			)
		);
	}

	@Override
	public PostConsensusCondition postConsensusCondition() {
		Set<Pair<String, ShardedAppStateId>> stateRequired = new HashSet<>();
		stateRequired.add(Pair.of("Balance 1", ShardedAppStateId.of(TokenBalanceState.class, apiAddress)));
		stateRequired.add(Pair.of("Balance 2", ShardedAppStateId.of(TokenBalanceState.class, toAddress)));

		return new PostConsensusCondition(
			stateRequired,
			new Condition<>(map -> {
				TokenBalanceState tokenBalanceState1 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, apiAddress));
				TokenBalanceState tokenBalanceState2 = (TokenBalanceState) map.get(ShardedAppStateId.of(TokenBalanceState.class, toAddress));
				return tokenBalanceState1.getBalance().get(tokenRef) == null &&
					tokenBalanceState2.getBalance().get(tokenRef).compareTo(BigDecimal.valueOf(2)) == 0;
			}, "Transfer of 2 JOSH from one account to another")
		);
	}
}
