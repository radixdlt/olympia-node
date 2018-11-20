package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class FungibleQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new FungibleQuark(1, 1, null)).isInstanceOf(NullPointerException.class);
	}
}