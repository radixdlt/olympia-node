package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.atoms.particles.StorageParticle;
import com.radixdlt.client.core.atoms.particles.StorageParticle.StorageParticleBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StorageParticleTest {
	@Test
	public void testApplicationMetaData() {
		StorageParticle storageParticle = new StorageParticleBuilder()
			.payload(new byte[0])
			.setMetaData("application", "test")
			.build();
		assertEquals("test", storageParticle.getMetaData("application"));
		assertNull(storageParticle.getMetaData("missing"));
	}

	@Test
	public void testNullDataParticle() {
		assertThatThrownBy(() -> new StorageParticleBuilder().setMetaData("application", "hello").build())
			.isInstanceOf(NullPointerException.class);
	}
}