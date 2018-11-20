package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class UniqueQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new UniqueQuark(null));
	}
}