package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.translate.tokens.TransferTokensToParticlesMapper;
import java.math.BigDecimal;
import org.junit.Test;
import com.radixdlt.client.application.actions.TransferTokensAction;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import java.util.Collections;

public class TransferTokensActionTranslatorTest {

	@Test
	public void createTransactionWithNoFunds() {
		RadixUniverse universe = mock(RadixUniverse.class);
		RadixAddress address = mock(RadixAddress.class);

		TransferTokensToParticlesMapper transferTranslator = new TransferTokensToParticlesMapper(universe);

		TokenClassReference token = mock(TokenClassReference.class);
		when(token.getSymbol()).thenReturn("TEST");

		TransferTokensAction transferTokensAction = mock(TransferTokensAction.class);
		when(transferTokensAction.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(transferTokensAction.getFrom()).thenReturn(address);
		when(transferTokensAction.getTokenClassReference()).thenReturn(token);

		TokenBalanceState state = mock(TokenBalanceState.class);
		when(state.getBalance()).thenReturn(Collections.emptyMap());

		assertThatThrownBy(() -> transferTranslator.map(transferTokensAction, state))
			.isEqualTo(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

}