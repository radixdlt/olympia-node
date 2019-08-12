package org.radix.network2.messaging;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;
import org.radix.modules.Modules;
import org.radix.modules.exceptions.ModuleException;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.messaging.Messaging;
import org.radix.network.peers.PeerStore;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportListener;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.properties.RuntimeProperties;
import org.xerial.snappy.Snappy;

import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MessageCentralImplTest {

	static class DummyTransportListener implements TransportListener {
		private InboundMessageConsumer messageSink = null;
		private boolean isClosed = false;

		@Override
		public void start(InboundMessageConsumer messageSink) {
			this.messageSink = messageSink;
		}

		@Override
		public void close() throws IOException {
			this.isClosed = true;
		}

		void inboundMessage(InboundMessage msg) {
			messageSink.accept(msg);
		}
	}

	static class DummyTransportOutboundConnection implements TransportOutboundConnection {
		private boolean sent = false;

		@Override
		public void close() throws IOException {
			// Ignore for now
		}

		@Override
		public CompletableFuture<SendResult> send(byte[] data) {
			sent = true;
			return CompletableFuture.completedFuture(SendResult.complete());
		}
	}

	private Serialization serialization;
	private DummyTransportOutboundConnection toc;
	private DummyTransportListener dtl;
	private ConnectionManager connectionManager;
	private MessageCentralImpl mci;

	@Before
	public void testSetup() throws ModuleException {
		this.serialization = Serialization.getDefault();

		// Curse you singletons
		Universe universe = mock(Universe.class);
		when(universe.getMagic()).thenReturn(0);
		RuntimeProperties runtimeProperties = mock(RuntimeProperties.class);
		when(runtimeProperties.get(eq("network.whitelist"), any())).thenReturn("");
		when(runtimeProperties.get(eq("messaging.inbound.queue_max"), any())).thenReturn(8192);
		when(runtimeProperties.get(eq("messaging.outbound.queue_max"), any())).thenReturn(16384);
		when(runtimeProperties.get(eq("messaging.time_to_live"), any())).thenReturn(30);
    	when(runtimeProperties.get(eq("network.udp.buffer"), any())).thenReturn(1 << 18);

		PeerStore peerStore = mock(PeerStore.class);
		Modules.put(Universe.class, universe);
		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(PeerStore.class, peerStore);
		Modules.put(Serialization.class, serialization);

		// This is especially horrible
		Messaging.getInstance().start_impl();

		// Other scaffolding

		this.toc = new DummyTransportOutboundConnection();

		// Warning suppression OK here -> mocked interfaces don't have contained resources
		@SuppressWarnings("resource")
		TransportControl transportControl = mock(TransportControl.class);
		when(transportControl.open()).thenReturn(CompletableFuture.completedFuture(this.toc));

		// Warning suppression OK here -> mocked interfaces don't have contained resources
		@SuppressWarnings("resource")
		Transport transport = mock(Transport.class);
		when(transport.control()).thenReturn(transportControl);

		this.connectionManager = mock(ConnectionManager.class);
		when(connectionManager.findTransport(any(), any())).thenReturn(transport);

		this.dtl = new DummyTransportListener();

		List<TransportListener> listeners = ImmutableList.of(this.dtl);

		this.mci = new MessageCentralImpl(serialization, connectionManager, listeners);
	}

	@After
	public void cleanup() throws ModuleException {
		this.mci.close();

		// This is especially horrible
		Messaging.getInstance().stop_impl();

		Modules.remove(Universe.class);
		Modules.remove(RuntimeProperties.class);
		Modules.remove(PeerStore.class);
		Modules.remove(Serialization.class);
	}

	@Test
	public void testConstructNoListeners() {
		try (MessageCentralImpl mci2 = new MessageCentralImpl(Serialization.getDefault(), connectionManager, ImmutableList.of())) {
			assertNull(Whitebox.getInternalState(mci2, "processingThread"));
		}
	}

	@Test
	public void testConstruct() {
		// Make sure start called on our transport
		assertNotNull(dtl.messageSink);
	}

	@Test
	public void testClose() {
		mci.close();
		assertTrue(dtl.isClosed);
	}

	@Test
	public void testSend() {
		Message msg = new TestMessage();
		mci.send(null, msg);
		assertTrue(toc.sent);
	}

	@Test
	public void testInbound() throws IOException, InterruptedException {
		Message msg = new TestMessage();
		byte[] data = Snappy.compress(serialization.toDson(msg, Output.WIRE));

		AtomicReference<Message> receivedMessage = new AtomicReference<>();
		Semaphore receivedFlag = new Semaphore(0);

		Messaging.getInstance().register(msg.getCommand(), (messsage, peer) -> {
			receivedMessage.set(messsage);
			receivedFlag.release();
		});

		TransportInfo source = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		InboundMessage message = InboundMessage.of(source, data);
		dtl.inboundMessage(message);

		assertTrue(receivedFlag.tryAcquire(500, TimeUnit.MILLISECONDS));
		assertNotNull(receivedMessage.get());
	}

	// FIXME: Not yet implemented
	@Test(expected=IllegalStateException.class)
	public void testAddListener() {
		mci.addListener(null, null);
	}

	// FIXME: Not yet implemented
	@Test(expected=IllegalStateException.class)
	public void testRemoveListener() {
		mci.removeListener(null, null);
	}

}
