package com.radixdlt.integration.serialization;

import com.radixdlt.network.messaging.Message;
import com.radixdlt.network.messaging.serialization.CompressedMessageSerialization;
import com.radixdlt.network.messaging.serialization.MessageSerialization;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.proxy.ProxyCertificateManager;
import org.junit.Test;
import com.radixdlt.network.p2p.liveness.messages.PeerPingMessage;
import org.radix.time.Time;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.messaging.EventQueueFactory;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.messaging.MessageCentralConfiguration;
import com.radixdlt.network.messaging.MessageCentralImpl;
import com.radixdlt.network.messaging.OutboundMessageEvent;
import com.radixdlt.network.messaging.SimplePriorityBlockingQueue;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.utils.Compress;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.subjects.PublishSubject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MessageCentralFuzzyTest {
	private static final int MIN_MESSAGE_LEN = 1;
	private static final int MAX_MESSAGE_LEN = 1024 * 1024;
	private static final int NUM_TEST_MESSAGES = 1000;

	private final Random random = new SecureRandom();
	private final MessageSerialization serialization =
		new CompressedMessageSerialization(DefaultSerialization.getInstance());

	@Test
	@SuppressWarnings("unchecked")
	public void fuzzy_messaged_are_not_accepted() throws Exception {
		final var self = ECKeyPair.generateNew();
		var inboundMessages = PublishSubject.<InboundMessage>create();
		var messageCentralConfig = mock(MessageCentralConfiguration.class);
		var p2pConfig = mock(P2PConfig.class);
		var peerControl = mock(PeerControl.class);
		var peerManager = mock(PeerManager.class);
		var proxyCertificateManager = mock(ProxyCertificateManager.class);
		var queueFactory = mock(EventQueueFactory.class);

		when(messageCentralConfig.messagingOutboundQueueMax(anyInt())).thenReturn(1);
		when(messageCentralConfig.messagingTimeToLive(anyLong())).thenReturn(30_000L);
		when(peerManager.messages()).thenReturn(inboundMessages);

		when(queueFactory.createEventQueue(anyInt(), any(Comparator.class)))
			.thenReturn(new SimplePriorityBlockingQueue<>(1, OutboundMessageEvent.comparator()));

		var messageCentral = new MessageCentralImpl(
			NodeId.fromPublicKey(self.getPublicKey()),
			"self",
			messageCentralConfig,
			p2pConfig,
			serialization,
			peerManager,
			proxyCertificateManager,
			Time::currentTimestamp,
			queueFactory,
			new SystemCountersImpl(),
			() -> peerControl
		);

		var counter = new AtomicLong(0);

		var disposable = messageCentral.messagesOf(Message.class)
			.subscribe(nextItem -> counter.incrementAndGet(), error -> fail(error.getMessage()));

		//Insert single valid message to ensure whole pipeline is working properly
		emitSingleValidMessage(inboundMessages);
		// Insert batch of randomly generated messages
		emitFuzzyMessages(inboundMessages);

		disposable.dispose();

		// Ensure that only one (valid) message passed through
		assertEquals(1L, counter.get());
	}

	private void emitSingleValidMessage(PublishSubject<InboundMessage> subject) {
		try {
			var bytes = serialization.serialize(new PeerPingMessage()).toOptional().get();
			var valid = new InboundMessage(Time.currentTimestamp(), randomNodeId(), bytes);
			subject.onNext(valid);
		} catch (Exception e) {
			// Ignore
		}
	}

	private void emitFuzzyMessages(PublishSubject<InboundMessage> subject) {
		for (int i = 0; i < NUM_TEST_MESSAGES; i++) {
			subject.onNext(generateFuzzyMessage());
		}

		subject.onComplete();
	}

	private InboundMessage generateFuzzyMessage() {
		while (true) {
			try {
				var compressedMessage = Compress.compress(generateRandomBytes());
				return new InboundMessage(Time.currentTimestamp(), randomNodeId(), compressedMessage);
			} catch (Exception e) {
				// Ignore exception and generate new message
			}
		}
	}

	private NodeId randomNodeId() {
		return NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
	}

	private byte[] generateRandomBytes() {
		var len = random.nextInt(MIN_MESSAGE_LEN, MAX_MESSAGE_LEN);
		var result = new byte[len];

		for (int i = 0; i < len; i++) {
			result[i] = (byte) random.nextInt(0, 255);
		}

		return result;
	}
}
