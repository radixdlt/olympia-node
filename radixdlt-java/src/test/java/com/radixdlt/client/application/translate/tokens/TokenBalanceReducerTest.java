package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;

import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		OwnedTokensParticle ownedTokensParticle = mock(OwnedTokensParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(ownedTokensParticle.getAmount()).thenReturn(UInt256.TEN);
		when(ownedTokensParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(ownedTokensParticle.getHash()).thenReturn(hash);
		when(ownedTokensParticle.getDson()).thenReturn(new byte[] {1});
		TokenTypeReference token = mock(TokenTypeReference.class);
		when(ownedTokensParticle.getTokenTypeReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), SpunParticle.up(ownedTokensParticle));
		BigDecimal tenSubunits = TokenTypeReference.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(tenSubunits)).isEqualTo(0);
	}
}