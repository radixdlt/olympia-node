package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import java.math.BigDecimal;

import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.core.atoms.RadixHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.radixdlt.client.core.ledger.TransitionedParticle;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		TransferrableTokensParticle minted = mock(TransferrableTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(minted.getAmount()).thenReturn(UInt256.TEN);
		when(minted.getGranularity()).thenReturn(UInt256.ONE);
		when(minted.getHash()).thenReturn(hash);
		TokenDefinitionReference token = mock(TokenDefinitionReference.class);
		when(minted.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(minted));
		BigDecimal tenSubunits = TokenUnitConversions.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(tenSubunits)).isEqualTo(0);
	}

	@Test
	public void transitionAndRevertTest() {
		TransferrableTokensParticle ownedTokensParticle = mock(TransferrableTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getGranularity()).thenReturn(UInt256.ONE);
		TokenDefinitionReference token = mock(TokenDefinitionReference.class);
		when(ownedTokensParticle.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance0 = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(ownedTokensParticle));
		TokenBalanceState tokenBalance1 = reducer.reduce(tokenBalance0, TransitionedParticle.u2n(ownedTokensParticle));


		assertThat(tokenBalance1.getBalance().get(token).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(tokenBalance1.getBalance().get(token).unconsumedTransferrable().count()).isEqualTo(0);
	}

	@Test
	public void twoTransitionsAndRevertTest() {
		TransferrableTokensParticle ownedTokensParticle = mock(TransferrableTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getGranularity()).thenReturn(UInt256.ONE);
		TokenDefinitionReference token = mock(TokenDefinitionReference.class);
		when(ownedTokensParticle.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance0 = reducer.reduce(new TokenBalanceState(), TransitionedParticle.n2u(ownedTokensParticle));
		TokenBalanceState tokenBalance1 = reducer.reduce(tokenBalance0, TransitionedParticle.u2d(ownedTokensParticle));
		TokenBalanceState tokenBalance2 = reducer.reduce(tokenBalance1, TransitionedParticle.d2u(ownedTokensParticle));

		BigDecimal tenSubunits = TokenUnitConversions.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance1.getBalance().get(token).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(tokenBalance1.getBalance().get(token).unconsumedTransferrable().count()).isEqualTo(0);
		assertThat(tokenBalance2.getBalance().get(token).getAmount()).isEqualByComparingTo(tenSubunits);
		assertThat(tokenBalance2.getBalance().get(token).unconsumedTransferrable().findFirst()).contains(ownedTokensParticle);
	}
}