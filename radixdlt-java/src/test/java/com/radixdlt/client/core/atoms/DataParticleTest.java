package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class DataParticleTest {
	@Test
	public void testApplicationMetaData() {
		Payload payload = mock(Payload.class);
		DataParticle dataParticle = new DataParticle(payload, "test");
		assertEquals("test", dataParticle.getMetaData("application"));
		assertNull(dataParticle.getMetaData("missing"));
	}

	@Test
	public void testNullDataParticle() {
		assertThatThrownBy(() -> new DataParticle(null, "hello"))
			.isInstanceOf(NullPointerException.class);
	}
}