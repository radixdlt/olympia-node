package org.radix.network2.transport;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.radix.network.peers.Peer;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportFactory;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.UDPOnlyConnectionManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;

public class UDPOnlyConnectionManagerTest {

	private AtomicInteger closed;
	private UDPOnlyConnectionManager connectionManager;

	@Before
	public void setUp() throws Exception {
		// Mocked class doesn't need closing
		@SuppressWarnings("resource")
		Transport t = mock(Transport.class);
		doAnswer(invocation -> closed.incrementAndGet()).when(t).close();
		TransportFactory tf = mock(TransportFactory.class);
		when(tf.create(any())).thenReturn(t);
		this.connectionManager = new UDPOnlyConnectionManager(tf);
		this.closed = new AtomicInteger(0);
	}

	@Test
	public void testFindTransport() {
		byte[] dummyMessage = new byte[0];

		TransportMetadata tm1 = mock(TransportMetadata.class);
		Peer peer1 = mock(Peer.class);
		when(peer1.connectionData(any())).thenReturn(tm1);
		assertNotNull(connectionManager.findTransport(peer1, dummyMessage));
		assertThat(connectionManager.size(), equalTo(1));

		TransportMetadata tm2 = mock(TransportMetadata.class);
		Peer peer2 = mock(Peer.class);
		when(peer2.connectionData(any())).thenReturn(tm2);
		assertNotNull(connectionManager.findTransport(peer2, dummyMessage));
		assertThat(connectionManager.size(), equalTo(2));
	}

	@Test
	public void testClose() throws IOException {
		byte[] dummyMessage = new byte[0];

		TransportMetadata tm1 = mock(TransportMetadata.class);
		Peer peer1 = mock(Peer.class);
		when(peer1.connectionData(any())).thenReturn(tm1);
		connectionManager.findTransport(peer1, dummyMessage);
		assertThat(connectionManager.size(), equalTo(1)); // Check

		TransportMetadata tm2 = mock(TransportMetadata.class);
		Peer peer2 = mock(Peer.class);
		when(peer2.connectionData(any())).thenReturn(tm2);
		connectionManager.findTransport(peer2, dummyMessage);
		assertThat(connectionManager.size(), equalTo(2)); // Check

		assertThat(closed.get(), equalTo(0)); // Precondition
		connectionManager.close(); // Close everything
		assertThat(closed.get(), equalTo(2));
	}

}
