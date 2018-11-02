package com.radixdlt.client.atommodel.quarks;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Map;

public class DataQuarkTest {
	@Test
	public void testNullConstruction() {
		Assertions.assertThatThrownBy(() -> new DataQuark((String) null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> new DataQuark((byte[]) null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> new DataQuark((String) null, null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> new DataQuark(null, (Map<String, String>) null)).isInstanceOf(NullPointerException.class);
		Assertions.assertThatThrownBy(() -> new DataQuark((byte[]) null, (String) null)).isInstanceOf(NullPointerException.class);
	}
}