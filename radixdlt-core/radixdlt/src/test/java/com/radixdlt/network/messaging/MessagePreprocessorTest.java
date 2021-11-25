package com.radixdlt.network.messaging;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.radix.network.messaging.Message;
import org.radix.time.Time;

import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.HighQC;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.ledger.DtoTxnsAndProof;
import com.radixdlt.middleware2.network.ConsensusEventMessage;
import com.radixdlt.middleware2.network.GetVerticesErrorResponseMessage;
import com.radixdlt.middleware2.network.GetVerticesRequestMessage;
import com.radixdlt.middleware2.network.GetVerticesResponseMessage;
import com.radixdlt.middleware2.network.LedgerStatusUpdateMessage;
import com.radixdlt.middleware2.network.MempoolAddMessage;
import com.radixdlt.middleware2.network.StatusResponseMessage;
import com.radixdlt.middleware2.network.SyncRequestMessage;
import com.radixdlt.middleware2.network.SyncResponseMessage;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.functional.Tuple.Tuple2;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.radixdlt.utils.SerializerTestDataGenerator.randomProposal;
import static com.radixdlt.utils.SerializerTestDataGenerator.randomVote;
import static com.radixdlt.utils.functional.Tuple.tuple;

import static java.security.AccessController.doPrivileged;

@RunWith(Parameterized.class)
public class MessagePreprocessorTest {
	//Classes which have no zero-parameter constructors are excluded:
	// GetPeersMessage PeerPingMessage PeerPongMessage StatusRequestMessage

	//Classes which are valid regardless from constructor parameters are excluded
	// PeersResponseMessage

	@SuppressWarnings("unchecked")
	private static final List<Tuple2<Message, String>> TEST_VECTORS = List.of(
		tuple(new GetVerticesErrorResponseMessage(mock(HighQC.class), mock(GetVerticesRequestMessage.class)), "highQC"),
		tuple(new GetVerticesErrorResponseMessage(mock(HighQC.class), mock(GetVerticesRequestMessage.class)), "request"),
		tuple(new GetVerticesRequestMessage(mock(HashCode.class), 1), "vertexId"),
		tuple(new GetVerticesResponseMessage(mock(List.class)), "vertices"),
		tuple(new LedgerStatusUpdateMessage(mock(LedgerProof.class)), "header"),
		tuple(new MempoolAddMessage(mock(List.class)), "txns"),
		tuple(new StatusResponseMessage(mock(LedgerProof.class)), "header"),
		tuple(new SyncRequestMessage(mock(DtoLedgerProof.class)), "currentHeader"),
		tuple(new SyncResponseMessage(mock(DtoTxnsAndProof.class)), "commands")
	);
	private static final Serialization SERIALIZATION = DefaultSerialization.getInstance();

	private final SystemCountersImpl counters = new SystemCountersImpl();
	private final MessageCentralConfiguration config = mock(MessageCentralConfiguration.class);
	private final PeerControl peerControl = mock(PeerControl.class);
	private final MessagePreprocessor messagePreprocessor = new MessagePreprocessor(
		counters,
		config,
		System::currentTimeMillis,
		SERIALIZATION,
		() -> peerControl
	);

	private final Class<?> clazz;
	private final InboundMessage inboundMessage;

	public MessagePreprocessorTest(Class<?> clazz, InboundMessage inboundMessage) {
		this.clazz = clazz;
		this.inboundMessage = inboundMessage;
	}

	@Parameters
	public static Collection<Object[]> testParameters() {
		var consensusMessages = Stream.of(
			prepareTestMessage(new ConsensusEventMessage(randomProposal()), "proposal"),
			prepareTestMessage(new ConsensusEventMessage(randomProposal()), "vote", randomVote()),
			prepareTestMessage(new ConsensusEventMessage(randomVote()), "vote"),
			prepareTestMessage(new ConsensusEventMessage(randomVote()), "proposal", randomProposal())
		);

		return Streams.concat(
			TEST_VECTORS.stream()
				.map(tuple -> tuple.map(MessagePreprocessorTest::prepareTestMessage)),
			consensusMessages
		).toList();
	}

	private static Object[] prepareTestMessage(Message message, String field) {
		return prepareTestMessage(message, field, null);
	}

	private static Object[] prepareTestMessage(Message message, String field, Object value) {
		var source = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
		var inboundMessage = generateMessage(source, message, field, value);

		return new Object[]{message.getClass(), inboundMessage};
	}

	private static InboundMessage generateMessage(NodeId source, Message message, String field, Object value) {
		try {
			setField(message, field, value);
		} catch (Exception e) {
			fail("Unable to set field " + field
					 + " for message of type " + message.getClass()
					 + " because of " + e.getMessage());

			throw new RuntimeException("unreachable"); // tame compiler
		}

		return new InboundMessage(Time.currentTimestamp(), source, serialize(message));
	}

	private static byte[] serialize(Message message) {
		try {
			return Compress.compress(SERIALIZATION.toDson(message, DsonOutput.Output.WIRE));
		} catch (IOException e) {
			fail("Unable to serialize message of type " + message.getClass()
					 + " because of " + e.getMessage());
			throw new RuntimeException("unreachable"); // tame compiler
		}
	}

	private static void setField(Object instance, String fieldName, Object toSet) throws Exception {
		var field = instance.getClass().getDeclaredField(fieldName);

		doPrivileged((PrivilegedAction<Void>) () -> {
			field.setAccessible(true);
			return null;
		});

		field.set(instance, toSet);
	}

	@Test
	public void invalid_message_is_not_accepted_and_peer_is_banned() {
		System.out.println("Checking: " + clazz.getSimpleName());
		var result = messagePreprocessor.process(inboundMessage);

		assertFalse(result.isSuccess());

		verify(peerControl).banPeer(eq(inboundMessage.source()), eq(Duration.ofMinutes(5)), anyString());
	}
}
