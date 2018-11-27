package com.radixdlt.client.application.translate.tokens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.application.translate.tokens.TransferTokensAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import java.math.BigDecimal;
import org.junit.Test;

public class TransferTokensActionTest {

	@Test
	public void testBadBigDecimalScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenClassReference tokenClassReference = mock(TokenClassReference.class);

		assertThatThrownBy(() -> TransferTokensAction.create(from, to, new BigDecimal("0.000001"), tokenClassReference))
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