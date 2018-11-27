package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.translate.tokens.TokenBalanceReducer;
import com.radixdlt.client.application.translate.tokens.TokenBalanceState;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import org.junit.Test;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		OwnedTokensParticle ownedTokensParticle = mock(OwnedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(10L);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getDson()).thenReturn(new byte[] {1});
		TokenClassReference token = mock(TokenClassReference.class);
		when(ownedTokensParticle.getTokenClassReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), SpunParticle.up(ownedTokensParticle));
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(TokenClassReference.subUnitsToDecimal(10L))).isEqualTo(0);
	}
}