package com.radixdlt.client.core.atoms;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.message.MessageParticle;
import com.radixdlt.client.atommodel.message.MessageParticle.MessageParticleBuilder;
import org.junit.Test;
import org.radix.common.ID.EUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageParticleTest {
	@Test
	public void testApplicationMetaData() {
		RadixAddress address = mock(RadixAddress.class);
		when(address.getUID()).thenReturn(mock(EUID.class), mock(EUID.class));

		MessageParticle messageParticle = new MessageParticleBuilder()
			.payload(new byte[0])
			.from(address)
			.to(address)
			.metaData("application", "test")
			.build();
		assertEquals("test", messageParticle.getMetaData("application"));
		assertNull(messageParticle.getMetaData("missing"));
	}

	@Test
	public void testNullDataParticle() {
		assertThatThrownBy(() -> new MessageParticleBuilder().metaData("application", "hello").build())
			.isInstanceOf(NullPointerException.class);
	}
}