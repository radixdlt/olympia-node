package org.radix.network2.messaging;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.radix.events.Events;
import org.radix.modules.Modules;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;
import org.radix.network.peers.PeerStore;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.properties.RuntimeProperties;
import org.radix.shards.ShardSpace;
import org.radix.state.State;
import org.xerial.snappy.Snappy;

import com.google.common.collect.ImmutableList;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MessageCentralImplTest {

	static class DummyTransportOutboundConnection implements TransportOutboundConnection {
		private boolean sent = false;
		private final Semaphore sentSemaphore = new Semaphore(0);

		@Override
		public void close() throws IOException {
			// Ignore for now
		}

		@Override
		public CompletableFuture<SendResult> send(byte[] data) {
			sent = true;
			sentSemaphore.release();
			return CompletableFuture.completedFuture(SendResult.complete());
		}
	}

	static class DummyTransport implements Transport {
		private InboundMessageConsumer messageSink = null;
		private boolean isClosed = false;

		private final TransportOutboundConnection out;

		DummyTransport(TransportOutboundConnection out) {
			this.out = out;
		}

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

		@Override
		public String name() {
			return "DUMMY";
		}

		@Override
		public TransportControl control() {
			return new TransportControl() {
				@Override
				public CompletableFuture<TransportOutboundConnection> open(TransportMetadata ignored) {
					return CompletableFuture.completedFuture(out);
				}

				@Override
				public void close() throws IOException {
					// Nothing to do
				}
			};
		}

		@Override
		public TransportMetadata localMetadata() {
			return StaticTransportMetadata.empty();
		}
	}

	private Serialization serialization;
	private DummyTransportOutboundConnection toc;
	private DummyTransport dt;
	private TransportManager transportManager;
	private MessageCentralImpl mci;

	@Before
	public void testSetup() {
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
    	// Following required by Network -> LocalSystem dependency
    	when(runtimeProperties.get(eq("node.key.path"), any())).thenReturn("node.key");
    	when(runtimeProperties.get(eq("shards.range"), any())).thenReturn(ShardSpace.SHARD_CHUNK_RANGE);

		PeerStore peerStore = mock(PeerStore.class);
		Modules.put(Universe.class, universe);
		Modules.put(RuntimeProperties.class, runtimeProperties);
		Modules.put(PeerStore.class, peerStore);
		Modules.put(Serialization.class, serialization);
		Modules.put(SecureRandom.class, new SecureRandom());

		// Other scaffolding
		this.toc = new DummyTransportOutboundConnection();
		this.dt = new DummyTransport(this.toc);

		this.transportManager = new TransportManager() {

			@Override
			public void close() throws IOException {
				// Nothing
			}

			@Override
			public List<Transport> transports() {
				return ImmutableList.of(MessageCentralImplTest.this.dt);
			}

			@Override
			public Transport findTransport(Peer peer, byte[] bytes) {
				return MessageCentralImplTest.this.dt;
			}
		};

		Events events = mock(Events.class);
		this.mci = new MessageCentralImpl(staticConfig(), serialization, transportManager, events);
	}

	@After
	public void cleanup() {
		Modules.remove(Universe.class);
		Modules.remove(RuntimeProperties.class);
		Modules.remove(PeerStore.class);
		Modules.remove(Serialization.class);
		Modules.remove(SecureRandom.class);

		this.mci.close();
	}

	@Test
	public void testConstruct() {
		// Make sure start called on our transport
		assertNotNull(dt.messageSink);
	}

	@Test
	public void testClose() {
		mci.close();
		assertTrue(dt.isClosed);
	}

	@Test
	public void testSend() throws InterruptedException {
		Message msg = new TestMessage();
		Peer peer = mock(Peer.class);
		doReturn(new State(State.CONNECTED)).when(peer).getState();
		mci.send(peer, msg);
		assertTrue(toc.sentSemaphore.tryAcquire(10, TimeUnit.SECONDS));
		assertTrue(toc.sent);
	}

	@Test
	public void testInbound() throws IOException, InterruptedException {
		Message msg = new TestMessage();
		byte[] data = Snappy.compress(serialization.toDson(msg, Output.WIRE));

		AtomicReference<Message> receivedMessage = new AtomicReference<>();
		Semaphore receivedFlag = new Semaphore(0);

		mci.addListener(msg.getClass(), (peer, messsage) -> {
			receivedMessage.set(messsage);
			receivedFlag.release();
		});

		TransportInfo source = TransportInfo.of("DUMMY", StaticTransportMetadata.empty());

		InboundMessage message = InboundMessage.of(source, data);
		dt.inboundMessage(message);

		assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
		assertNotNull(receivedMessage.get());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddNullListener() {
		mci.addListener(TestMessage.class, null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddListenerTwice() {
		MessageListener<TestMessage> listener = (source, message) -> {};
		mci.addListener(TestMessage.class, listener);
		mci.addListener(TestMessage.class, listener);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRemoveNullListener() {
		mci.removeListener(TestMessage.class, null);
	}

	public void testAddRemoveListener() {
		MessageListener<TestMessage> listener = (source, message) -> {};
		mci.addListener(TestMessage.class, listener);
		assertEquals(1, mci.listenersSize());
		mci.addListener(TestMessage.class, listener);
		assertEquals(0, mci.listenersSize());
	}

	private MessageCentralConfiguration staticConfig() {
		// More robust than mocking when adding new config
		return new MessageCentralConfiguration() {
			@Override
			public int getMessagingInboundQueueMax(int defaultValue) {
				return 10;
			}
			@Override
			public int getMessagingOutboundQueueMax(int defaultValue) {
				return 10;
			}
			@Override
			public int getMessagingTimeToLive(int defaultValue) {
				return 10;
			}
		};
	}
}
