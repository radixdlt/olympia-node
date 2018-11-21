package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class NonFungibleQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new NonFungibleQuark(null));
	}
}