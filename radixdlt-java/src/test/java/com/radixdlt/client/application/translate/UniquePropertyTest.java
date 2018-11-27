package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.application.translate.UniqueProperty;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import org.junit.Test;

public class UniquePropertyTest {
	@Test
	public void testNullUniqueProperty() {
		RadixAddress address = mock(RadixAddress.class);
		assertThatThrownBy(() -> new UniqueProperty(null, address))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UniqueProperty(new byte[] {}, null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testConstruction() {
		RadixAddress address = mock(RadixAddress.class);
		UniqueProperty uniqueProperty = new UniqueProperty(new byte[] {}, address);
		assertThat(uniqueProperty.getAddress()).isEqualTo(address);
	}
}