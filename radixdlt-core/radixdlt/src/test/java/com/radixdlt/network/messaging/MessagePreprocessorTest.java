package com.radixdlt.network.messaging;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MessagePreprocessorTest {
    // serialized version of SyncRequestMessage(null),
    // can't be created with code, as SyncRequestMessage does not accept null anymore
    private static final byte[] SYNC_REQUEST_MESSAGE = {
            -65, 98, 115, 122, 120, 25, 109, 101, 115, 115, 97, 103, 101, 46, 115, 121,
            110, 99, 46, 115, 121, 110, 99, 95, 114, 101, 113, 117, 101, 115, 116, -1
    };

    @Test
    public void invalid_message_is_not_accepted_and_peer_is_banned() {
        var counters = new SystemCountersImpl();
        var config = mock(MessageCentralConfiguration.class);
        var serialization = DefaultSerialization.getInstance();
        var peerControl = mock(PeerControl.class);
        var source = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());
        var inboundMessage = InboundMessage.of(source, SYNC_REQUEST_MESSAGE);

        var messagePreprocessor = new MessagePreprocessor(
                counters,
                config,
                System::currentTimeMillis,
                serialization,
                () -> peerControl
        );

        var result = messagePreprocessor.process(inboundMessage);

        assertFalse(result.isSuccess());

        verify(peerControl).banPeer(eq(source), eq(Duration.ofMinutes(5)), anyString());
    }
}
