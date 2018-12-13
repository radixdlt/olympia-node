package com.radixdlt.client.application.translate.tokens;

import java.math.BigDecimal;

import org.junit.Test;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class TransferTokensActionTest {

	@Test
	public void testBadBigDecimalScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);

		BigDecimal tooSmall = BigDecimal.valueOf(1L, TokenClassReference.SUB_UNITS_POW_10 + 1);

		assertThatThrownBy(() -> TransferTokensAction.create(from, to, tooSmall, tokenClassReference))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testSmallestAllowedAmount() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);

		assertThat(TransferTokensAction.create(from, to, new BigDecimal("0.00001"), tokenClassReference).toString()).isNotNull();
	}

	@Test
	public void testSmallestAllowedAmountLargeScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);

		assertThat(TransferTokensAction.create(from, to, new BigDecimal("0.000010000"), tokenClassReference).toString()).isNotNull();
	}
}