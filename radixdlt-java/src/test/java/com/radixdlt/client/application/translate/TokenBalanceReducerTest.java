package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.atoms.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import org.junit.Test;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		TransferParticle transferParticle = mock(TransferParticle.class);
		RadixHash hash = mock(RadixHash.class);
		when(transferParticle.getSignedAmount()).thenReturn(10L);
		when(transferParticle.getAmount()).thenReturn(10L);
		when(transferParticle.getHash()).thenReturn(hash);
		when(transferParticle.getSpin()).thenReturn(Spin.UP);
		when(transferParticle.getDson()).thenReturn(new byte[] {1});
		TokenClassReference token = mock(TokenClassReference.class);
		when(transferParticle.getTokenClassReference()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), transferParticle);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(TokenClassReference.subUnitsToDecimal(10L))).isEqualTo(0);
	}
}