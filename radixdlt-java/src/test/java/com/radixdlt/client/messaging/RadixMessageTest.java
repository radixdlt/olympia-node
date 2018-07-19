package com.radixdlt.client.messaging;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import org.junit.Test;

public class RadixMessageTest {
	@Test
	public void createReplyTest() {
		RadixAddress to = mock(RadixAddress.class);
		RadixAddress from = mock(RadixAddress.class);
		RadixMessageContent content = new RadixMessageContent(to, from, "Hello");
		Atom atom = mock(Atom.class);

		RadixMessage radixMessage = new RadixMessage(content, atom);
		RadixMessageContent reply = radixMessage.createReply("Hi");
		assertTrue(reply.getFrom() == radixMessage.getTo());
		assertTrue(reply.getTo() == radixMessage.getFrom());
		assertEquals("Hi", reply.getContent());
	}
}