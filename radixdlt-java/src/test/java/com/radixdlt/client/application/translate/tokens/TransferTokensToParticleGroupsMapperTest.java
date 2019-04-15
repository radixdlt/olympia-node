package com.radixdlt.client.application.translate.tokens;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.translate.ShardedAppStateId;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.RRI;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Collections;

public class TransferTokensToParticleGroupsMapperTest {

	@Test
	public void createTransactionWithNoFunds() {
		RadixAddress address = mock(RadixAddress.class);

		RRI token = mock(RRI.class);
		when(token.getName()).thenReturn("TEST");

		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		TransferTokensToParticleGroupsMapper transferTranslator = new TransferTokensToParticleGroupsMapper();

		TestObserver<ShardedAppStateId> contextTestObserver = TestObserver.create();
		transferTranslator.requiredState(transferTokensAction).subscribe(contextTestObserver);
		contextTestObserver
			.assertValue(ctx -> ctx.address().equals(address))
			.assertValue(ctx -> ctx.stateClass().equals(TokenBalanceState.class));

		TestObserver<ParticleGroup> testObserver = TestObserver.create();
		transferTranslator.mapToParticleGroups(transferTokensAction, Observable.just(Observable.just(state))).subscribe(testObserver);
		testObserver.assertError(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}