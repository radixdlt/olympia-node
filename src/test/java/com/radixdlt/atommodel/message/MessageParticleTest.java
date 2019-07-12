package com.radixdlt.atommodel.message;

import static org.mockito.Mockito.mock;

import com.radixdlt.atomos.RadixAddress;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class MessageParticleTest {
	@Test
	public void when_constructing_message_particle_without_source__exception_is_thrown() {
		RadixAddress from = mock(RadixAddress.class);
		Assertions.assertThatThrownBy(() ->
				new MessageParticle(null, from, new byte[0]))
				.isInstanceOf(NullPointerException.class);

		Assertions.assertThatThrownBy(() ->
				new MessageParticle(null, from, new byte[0], ""))
				.isInstanceOf(NullPointerException.class);
	}
}