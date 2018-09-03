package com.radixdlt.client.core.atoms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.crypto.ECPublicKey;
import org.junit.Test;

public class UniqueParticleTest {
	@Test
	public void testNullUniqueParticle() {
		ECPublicKey ecPublicKey = mock(ECPublicKey.class);
		assertThatThrownBy(() -> UniqueParticle.create(null, ecPublicKey))
			.isInstanceOf(NullPointerException.class);
	}
}