package org.radix.network2.messaging;

import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import nl.jqno.equalsverifier.EqualsVerifier;

public class InboundMessageTest {

	private final TransportInfo transportInfo = TransportInfo.of("TEST", StaticTransportMetadata.empty());
	private final byte[] message = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
	private InboundMessage inboundMessage;

	@Before
	public void setUp() throws Exception {
		this.inboundMessage = InboundMessage.of(this.transportInfo, this.message);
	}

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(InboundMessage.class).verify();
	}

	@Test
	public void testSource() {
		assertThat(inboundMessage.source(), equalTo(transportInfo));
	}

	@Test
	public void testMessage() {
		assertThat(inboundMessage.message(), equalTo(message));
	}

	@Test
	public void testToString() {
		assertThat(inboundMessage.toString(), containsString("TEST")); // Transport name
		assertThat(inboundMessage.toString(), containsString("000102030405060708090a")); // Message data in hex
	}
}
