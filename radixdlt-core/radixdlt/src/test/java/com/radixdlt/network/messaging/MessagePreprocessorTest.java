package com.radixdlt.network.messaging;

import org.junit.Test;
import org.radix.network.messaging.Message;
import org.radix.time.Time;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.ledger.DtoLedgerProof;
import com.radixdlt.middleware2.network.LedgerStatusUpdateMessage;
import com.radixdlt.middleware2.network.SyncRequestMessage;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;

import java.security.PrivilegedAction;
import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static java.security.AccessController.doPrivileged;

public class MessagePreprocessorTest {
	private final SystemCountersImpl counters = new SystemCountersImpl();
	private final MessageCentralConfiguration config = mock(MessageCentralConfiguration.class);
	private final Serialization serialization = DefaultSerialization.getInstance();
	private final PeerControl peerControl = mock(PeerControl.class);
	private final MessagePreprocessor messagePreprocessor = new MessagePreprocessor(counters, config, System::currentTimeMillis, serialization, () -> peerControl);

	@Test
	public void invalid_sync_request_message_is_not_accepted_and_peer_is_banned() throws Exception {
		var source = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
		var inboundMessage = generateMessage(source, new SyncRequestMessage(mock(DtoLedgerProof.class)), "currentHeader");
		var result = messagePreprocessor.process(inboundMessage);

		assertFalse(result.isSuccess());

		verify(peerControl).banPeer(eq(source), eq(Duration.ofMinutes(5)), anyString());
	}

	@Test
	public void invalid_ledger_status_update_message_is_not_accepted_and_peer_is_banned() throws Exception {
		var source = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
		var inboundMessage = generateMessage(source, new LedgerStatusUpdateMessage(mock(LedgerProof.class)), "header");
		var result = messagePreprocessor.process(inboundMessage);

		assertFalse(result.isSuccess());

		verify(peerControl).banPeer(eq(source), eq(Duration.ofMinutes(5)), anyString());
	}

	private static void setField(Object instance, String fieldName, Object toSet) throws Exception {
		var field = instance.getClass().getDeclaredField(fieldName);

		doPrivileged((PrivilegedAction<Void>) () -> {
			field.setAccessible(true);
			return null;
		});

		field.set(instance, toSet);
	}

	private InboundMessage generateMessage(NodeId source, Message message, String field) throws Exception {
		setField(message, field, null);

		return new InboundMessage(
			Time.currentTimestamp(),
			source,
			Compress.compress(serialization.toDson(message, DsonOutput.Output.WIRE)));
	}
}
