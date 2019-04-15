package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.atoms.particles.RRI;
import java.math.BigDecimal;
import org.junit.Test;

public class TokenOverMintExceptionTest {
	@Test
	public void when_token_over_mint_exception_initialized_with_null__null_pointer_exception_is_thrown() {
		assertThatThrownBy(() -> new TokenOverMintException(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), null, BigDecimal.ZERO, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), BigDecimal.ZERO, null, BigDecimal.ZERO))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new TokenOverMintException(mock(RRI.class), BigDecimal.ZERO, BigDecimal.ZERO, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_token_over_mint_exception_compared_to_similar_big_decimal_exception__equals_returns_true() {
		RRI ref = mock(RRI.class);
		TokenOverMintException e1 = new TokenOverMintException(ref, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.0"));
		TokenOverMintException e2 = new TokenOverMintException(ref, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1.00000"));
		assertThat(e1).isEqualTo(e2);
	}
}