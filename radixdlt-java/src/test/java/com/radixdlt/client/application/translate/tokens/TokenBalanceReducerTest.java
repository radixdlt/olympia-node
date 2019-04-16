package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;

import java.util.stream.Stream;
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
		RRI token = mock(RRI.class);
		when(minted.getTokenDefinitionReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), minted);
		BigDecimal tenSubunits = TokenUnitConversions.subunitsToUnits(UInt256.TEN);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(tenSubunits)).isEqualTo(0);
	}
}