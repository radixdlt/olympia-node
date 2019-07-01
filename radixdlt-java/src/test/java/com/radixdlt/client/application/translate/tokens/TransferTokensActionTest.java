package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.core.atoms.particles.RRI;
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
		RRI rri = mock(RRI.class);

		BigDecimal tooSmall = BigDecimal.valueOf(1L, TokenUnitConversions.getTokenScale() + 1);

		assertThatThrownBy(() -> TransferTokensAction.create(rri, from, to, tooSmall))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testSmallestAllowedAmount() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		RRI rri = mock(RRI.class);

		assertThat(TransferTokensAction.create(rri, from, to, new BigDecimal("0.00001")).toString()).isNotNull();
	}

	@Test
	public void testSmallestAllowedAmountLargeScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		RRI rri = mock(RRI.class);

		assertThat(TransferTokensAction.create(rri, from, to, new BigDecimal("0.000010000")).toString()).isNotNull();
	}
}