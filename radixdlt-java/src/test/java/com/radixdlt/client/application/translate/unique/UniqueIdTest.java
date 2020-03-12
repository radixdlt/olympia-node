package com.radixdlt.client.application.translate.unique;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;

public class UniqueIdTest {
	@Test
	public void when_unique_id_is_created_with_null__then_null_pointer_exception_is_thrown() {
		assertThatThrownBy(() -> new UniqueId(null, "test"))
			.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> new UniqueId(mock(RadixAddress.class), null))
			.isInstanceOf(NullPointerException.class);
	}
}