package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import org.junit.Test;

public class TokenBalanceReducerTest {

	@Test
	public void testSimpleBalance() {
		Consumable consumable = mock(Consumable.class);
		RadixHash hash = mock(RadixHash.class);
		when(consumable.getSignedAmount()).thenReturn(10L);
		when(consumable.getAmount()).thenReturn(10L);
		when(consumable.getHash()).thenReturn(hash);
		when(consumable.getSpin()).thenReturn(Spin.UP);
		when(consumable.getDson()).thenReturn(new byte[] {1});
		TokenRef token = mock(TokenRef.class);
		when(consumable.getTokenRef()).thenReturn(token);

		TokenBalanceReducer reducer = new TokenBalanceReducer();
		TokenBalanceState tokenBalance = reducer.reduce(new TokenBalanceState(), consumable);
		assertThat(tokenBalance.getBalance().get(token).getAmount().compareTo(TokenRef.subUnitsToDecimal(10L))).isEqualTo(0);
	}
}