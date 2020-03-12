package com.radixdlt.client.core.network.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;
import com.radixdlt.identifiers.EUID;

public class FetchAtomsRequestActionTest {
	@Test
	public void when_initializing_with_null__npe_should_be_thrown() {
		assertThatThrownBy(() -> FetchAtomsRequestAction.newRequest(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	public void when_initializing_with_address__shards_should_match() {
		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(EUID.ONE);
		assertThat(FetchAtomsRequestAction.newRequest(address).getShards()).containsExactly(EUID.ONE.getShard());
	}
}