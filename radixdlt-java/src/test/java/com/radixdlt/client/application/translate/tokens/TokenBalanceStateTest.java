package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.atommodel.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.junit.Test;
import org.radix.utils.UInt256;

public class TokenBalanceStateTest {
	@Test
	public void token_balance_with_only_burned_tokens__should_not_contain_any_transferrable_tokens() {
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);
		OwnedTokensParticle burnedToken = mock(OwnedTokensParticle.class);
		when(burnedToken.getAmount()).thenReturn(UInt256.ONE);
		when(burnedToken.getGranularity()).thenReturn(UInt256.ONE);
		when(burnedToken.getDson()).thenReturn(new byte[] {0});
		when(burnedToken.getTokenClassReference()).thenReturn(tokenClassReference);
		when(burnedToken.getType()).thenReturn(FungibleType.BURNED);

		TokenBalanceState nextState = TokenBalanceState.merge(new TokenBalanceState(), SpunParticle.up(burnedToken));
		assertThat(nextState.getBalance().get(tokenClassReference).unconsumedTransferrable()).isEmpty();
	}
}