package org.radix.network2.transport;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.udp.UDPConstants;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.FirstMatchTransportManager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.CoreMatchers.*;

public class FirstMatchTransportManagerTest {

	private AtomicInteger closed;
	private FirstMatchTransportManager transportManager;

	@Before
	public void setUp() throws Exception {
		// Mocked class doesn't need closing
		@SuppressWarnings("resource")
		Transport t1 = mock(Transport.class);
		doAnswer(invocation -> UDPConstants.UDP_NAME).when(t1).name();
		doAnswer(invocation -> closed.incrementAndGet()).when(t1).close();
		@SuppressWarnings("resource")
		Transport t2 = mock(Transport.class);
		doAnswer(invocation -> "DUMMY").when(t2).name();
		this.transportManager = new FirstMatchTransportManager(ImmutableSet.of(t1, t2));
		this.closed = new AtomicInteger(0);
	}

	@Test
	public void testFindDefaultTransport() {
		byte[] dummyMessage = new byte[0];

		TransportMetadata tm1 = mock(TransportMetadata.class);
		Peer peer1 = mock(Peer.class);
		when(peer1.connectionData(any())).thenReturn(tm1);
		assertNotNull(transportManager.findTransport(peer1.supportedTransports(), dummyMessage));
	}

	@Test
	public void testFindTransportWithSelection() {
		byte[] dummyMessage = new byte[0];

		TransportInfo dummyTransport = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());
		TransportInfo udpTransport = TransportInfo.of(UDPConstants.UDP_NAME, StaticTransportMetadata.empty());

		List<TransportInfo> transports = ImmutableList.of(dummyTransport, udpTransport);

		Peer peer1 = mock(Peer.class);
		doAnswer(invocation -> transports.stream()).when(peer1).supportedTransports();
		@SuppressWarnings("resource")
		Transport found = transportManager.findTransport(peer1.supportedTransports(), dummyMessage);
		assertNotNull(found);
		assertEquals(found.name(), "DUMMY");
	}

	@Test
	public void testClose() throws IOException {
		assertThat(closed.get(), equalTo(0)); // Precondition
		transportManager.close(); // Close everything
		assertThat(closed.get(), equalTo(1));
	}

	@Test
	public void testTransports() {
		Set<String> transports = transportManager.transports().stream()
			.map(Transport::name)
			.collect(Collectors.toSet());
		assertThat(transports, hasItem("DUMMY"));
		assertThat(transports, hasItem(UDPConstants.UDP_NAME));
	}


	@Test
	public void testToString() {
		String s = transportManager.toString();
		assertThat(s, containsString("DUMMY"));
		assertThat(s, containsString(UDPConstants.UDP_NAME));
	}
}
