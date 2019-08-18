package org.radix.network2.transport;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.radix.network.peers.Peer;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.FirstMatchTransportManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;

public class FirstMatchConnectionManagerTest {

	private AtomicInteger closed;
	private FirstMatchTransportManager connectionManager;

	@Before
	public void setUp() throws Exception {
		// Mocked class doesn't need closing
		@SuppressWarnings("resource")
		Transport t = mock(Transport.class);
		doAnswer(invocation -> "UDP").when(t).name();
		doAnswer(invocation -> closed.incrementAndGet()).when(t).close();
		this.connectionManager = new FirstMatchTransportManager(Collections.singleton(t));
		this.closed = new AtomicInteger(0);
	}

	@Test
	public void testFindTransport() {
		byte[] dummyMessage = new byte[0];

		TransportMetadata tm1 = mock(TransportMetadata.class);
		Peer peer1 = mock(Peer.class);
		when(peer1.connectionData(any())).thenReturn(tm1);
		assertNotNull(connectionManager.findTransport(peer1, dummyMessage));
	}

	@Test
	public void testClose() throws IOException {
		assertThat(closed.get(), equalTo(0)); // Precondition
		connectionManager.close(); // Close everything
		assertThat(closed.get(), equalTo(1));
	}

}
