package com.radixdlt.client.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.address.RadixAddress;
import org.junit.Test;

public class RadixMessageTest {
	@Test
	public void toStringTest() {
		RadixAddress from = mock(RadixAddress.class);
		RadixAddress to = mock(RadixAddress.class);
		when(from.toString()).thenReturn("Me");
		when(to.toString()).thenReturn("You");
		RadixMessage message = new RadixMessage(from, to, "Hello", 0L);
		System.out.println(message.toString());
	}
}