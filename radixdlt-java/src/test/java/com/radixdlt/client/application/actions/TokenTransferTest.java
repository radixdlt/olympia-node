package com.radixdlt.client.application.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.TokenReference;
import java.math.BigDecimal;
import org.junit.Test;

public class TokenTransferTest {

	@Test
	public void testBadBigDecimalScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenReference tokenRef = mock(TokenReference.class);

		assertThatThrownBy(() -> TokenTransfer.create(from, to, new BigDecimal("0.000001"), tokenRef))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void testSmallestAllowedAmount() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenReference tokenRef = mock(TokenReference.class);

		assertThat(TokenTransfer.create(from, to, new BigDecimal("0.00001"), tokenRef).toString()).isNotNull();
	}

	@Test
	public void testSmallestAllowedAmountLargeScale() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		TokenReference tokenRef = mock(TokenReference.class);

		assertThat(TokenTransfer.create(from, to, new BigDecimal("0.000010000"), tokenRef).toString()).isNotNull();
	}
}