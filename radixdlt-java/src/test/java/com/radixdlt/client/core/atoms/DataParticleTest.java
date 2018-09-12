package com.radixdlt.client.core.atoms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.radixdlt.client.core.atoms.DataParticle.DataParticleBuilder;
import org.junit.Test;

public class DataParticleTest {
	@Test
	public void testApplicationMetaData() {
		Payload payload = mock(Payload.class);
		DataParticle dataParticle = new DataParticleBuilder()
			.payload(payload)
			.setMetaData("application", "test")
			.build();
		assertEquals("test", dataParticle.getMetaData("application"));
		assertNull(dataParticle.getMetaData("missing"));
	}

	@Test
	public void testNullDataParticle() {
		assertThatThrownBy(() -> new DataParticleBuilder().setMetaData("application", "hello").build())
			.isInstanceOf(NullPointerException.class);
	}
}