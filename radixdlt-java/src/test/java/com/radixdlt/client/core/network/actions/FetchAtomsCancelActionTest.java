package com.radixdlt.client.core.network.actions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.junit.Test;

public class FetchAtomsCancelActionTest {
	@Test
	public void when_initializing_with_null__npe_is_thrown() {
		assertThatThrownBy(() -> FetchAtomsCancelAction.of(null, mock(RadixAddress.class)))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> FetchAtomsCancelAction.of("Hi", null))
			.isInstanceOf(NullPointerException.class);
	}
}