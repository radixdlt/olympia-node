package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.radix.utils.UInt256;

public class FungibleQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new FungibleQuark(UInt256.ONE, 1, null)).isInstanceOf(NullPointerException.class);
	}
}