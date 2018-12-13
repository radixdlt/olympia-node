package com.radixdlt.client.application.translate.tokens;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.translate.StatefulActionToParticlesMapper.RequiredShardState;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import io.reactivex.Observable;
import io.reactivex.observers.TestObserver;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Collections;

public class TransferTokensToParticlesMapperTest {

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);


		TokenClassReference token = mock(TokenClassReference.class);
		when(token.getSymbol()).thenReturn("TEST");

		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenClassReference()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		TransferTokensToParticlesMapper transferTranslator = new TransferTokensToParticlesMapper(universe);

		TestObserver<RequiredShardState> contextTestObserver = TestObserver.create();
		transferTranslator.requiredState(transferTokensAction).subscribe(contextTestObserver);
		contextTestObserver
			.assertValue(ctx -> ctx.address().equals(address))
			.assertValue(ctx -> ctx.stateClass().equals(TokenBalanceState.class));

		TestObserver<SpunParticle> testObserver = TestObserver.create();
		transferTranslator.mapToParticles(transferTokensAction, Observable.just(Observable.just(state))).subscribe(testObserver);
		testObserver.assertError(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}