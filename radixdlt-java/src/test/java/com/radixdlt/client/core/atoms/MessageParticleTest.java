package com.radixdlt.client.core.atoms;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class MessageParticleTest {
	@Test
	public void testApplicationMetaData() {
		MessageParticle messageParticle = new MessageParticleBuilder()
			.payload(new byte[0])
			.source(mock(RadixAddress.class))
			.setMetaData("application", "test")
			.build();
		assertEquals("test", messageParticle.getMetaData("application"));
		assertNull(messageParticle.getMetaData("missing"));
	}

	@Test
	public void testNullDataParticle() {
		assertThatThrownBy(() -> new MessageParticleBuilder().setMetaData("application", "hello").build())
			.isInstanceOf(NullPointerException.class);
	}
}