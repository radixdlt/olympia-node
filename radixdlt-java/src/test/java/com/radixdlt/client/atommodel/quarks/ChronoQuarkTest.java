package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ChronoQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new ChronoQuark(null, 0L)).isInstanceOf(NullPointerException.class);
	}
}