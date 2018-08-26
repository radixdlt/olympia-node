package com.radixdlt.client.application.objects;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.client.application.objects.Data.DataBuilder;
import org.junit.Test;

public class DataTest {
	@Test
	public void builderNoBytesTest() {
		DataBuilder dataBuilder = new DataBuilder();
		assertThatThrownBy(dataBuilder::build).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void builderUnencryptedTest() {
		Data data = new DataBuilder().bytes(new byte[] {}).unencrypted().build();
		assertEquals(0, data.getBytes().length);
		assertTrue(data.getProtectors().isEmpty());
	}

	@Test
	public void builderNoReadersTest() {
		assertThatThrownBy(() -> new DataBuilder().bytes(new byte[] {}).build())
			.isInstanceOf(IllegalStateException.class);
	}
}