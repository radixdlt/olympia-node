package org.radix.network2.transport.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.modules.Modules;
import org.radix.network2.messaging.InboundMessage;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.properties.RuntimeProperties;

import com.radixdlt.universe.Universe;
import com.radixdlt.utils.Ints;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UDPTransportListenerImplTest {

	private BlockingQueue<InboundMessage> inboundMessages;
	private InboundMessageConsumer messageConsumer;
	private UDPTransportListenerImpl transportListener;
	private TestUDPSocket socket;

	static class TestUDPSocket implements UDPSocket {
		private boolean closed;
		private boolean interrupted;
		private final BlockingQueue<DatagramPacket> queue = new ArrayBlockingQueue<>(100);

		@Override
		public void close() throws IOException {
			this.closed = true;
		}

		@Override
		public boolean isClosed() {
			return this.closed;
		}

		@Override
		public void receive(DatagramPacket dp) throws IOException {
			try {
				DatagramPacket newdp = this.queue.take();
				dp.setData(newdp.getData());
				dp.setSocketAddress(newdp.getSocketAddress());
			} catch (InterruptedException e) {
				this.closed = true;
				throw new IOException();
			}
		}

		boolean isInterrupted() {
			return this.interrupted;
		}

		void submit(DatagramPacket dp) {
			this.queue.add(dp);
		}

	}

	@Before
	public void setUp() throws Exception {
		// Need interrupted flag to be clear for these tests to work
		assertFalse(Thread.currentThread().isInterrupted());

		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		when(runtimeProperties.get(eq("network.udp.buffer"), any())).thenReturn(100);
		Universe universe = mock(Universe.class);
		when(universe.getPort()).thenReturn(30000);

		Modules.put(Universe.class, universe);
		Modules.put(RuntimeProperties.class, runtimeProperties);

		StaticTransportMetadata metadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_UDP_HOST, "localhost",
			UDPConstants.METADATA_UDP_PORT, "30000"
		);
		this.socket = new TestUDPSocket();
		UDPSocketFactory socketFactory = mock(UDPSocketFactory.class);
		when(socketFactory.createServerSocket(any())).then(invocation -> this.socket);
		when(socketFactory.createClientChannel(any())).then(invocation -> mock(UDPChannel.class));
		this.transportListener = new UDPTransportListenerImpl(metadata, socketFactory);
		this.inboundMessages = new ArrayBlockingQueue<>(100);
		this.messageConsumer = this.inboundMessages::add;
	}

	@After
	public void tearDown() throws Exception {
		Modules.remove(RuntimeProperties.class);
		Modules.remove(Universe.class);
	}

	@Test
	public void testStart() throws IOException {
		transportListener.start(messageConsumer);
		Thread t = Whitebox.getInternalState(transportListener, "listeningThread");
		assertNotNull(t);
		assertTrue(t.isAlive());
		transportListener.close();
	}

	@Test
	public void testClose() throws IOException {
		transportListener.start(messageConsumer);
		Thread t = Whitebox.getInternalState(transportListener, "listeningThread");
		assertNotNull(t);
		assertTrue(t.isAlive());
		transportListener.close();
		assertFalse(t.isAlive());
	}

	@Test
	public void testReceiving() throws IOException, InterruptedException {
		assertFalse(Thread.interrupted());
		transportListener.start(messageConsumer);
		DatagramPacket dp = new DatagramPacket(Ints.toByteArray(0x12345678), 4, InetAddress.getLocalHost(), 30000);
		socket.submit(dp);
		InboundMessage msg = inboundMessages.poll(500, TimeUnit.MILLISECONDS);
		assertNotNull(msg);
		assertEquals(0x12345678, Ints.fromByteArray(msg.message()));
	}

}
