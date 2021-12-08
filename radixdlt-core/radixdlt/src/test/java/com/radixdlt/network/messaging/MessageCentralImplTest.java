package com.radixdlt.network.messaging;

import com.google.inject.Provider;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.middleware2.network.ConsensusEventMessage;
import com.radixdlt.network.messaging.serialization.CompressedMessageSerialization;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerManager;
import com.radixdlt.network.p2p.proxy.ProxyCertificateManager;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Compress;
import com.radixdlt.utils.TimeSupplier;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Comparator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessageCentralImplTest {

    @Mock
    private MessageCentralConfiguration messageCentralConfig;

    @Mock
    private Serialization serialization;

    @Mock
    private PeerManager peerManager;

    @Mock
    private ProxyCertificateManager proxyCertificateManager;

    @Mock
    private InboundMessage inboundMessage;

    @Mock
    private TimeSupplier timeSupplier;

    @Mock
    private EventQueueFactory<OutboundMessageEvent> outboundEventQueueFactory;

    @Mock
    private SystemCounters systemCounters;

    @Mock
    private Provider<PeerControl> peerControl;

    @Test
    public void when_messagesOf_is_called__then_underlying_pipeline_should_run_on_a_dedicated_thread_pool() throws Exception {
        // given
        when(messageCentralConfig.messagingOutboundQueueMax(anyInt())).thenReturn(1);

        when(serialization.fromDson(any(byte[].class), eq(Message.class)))
            .thenReturn(mock(ConsensusEventMessage.class));

        when(inboundMessage.message()).thenReturn(Compress.compress("".getBytes()));
        when(inboundMessage.source()).thenReturn(mock(NodeId.class));

        Observable<InboundMessage> inboundMessages = Observable.create(emitter -> {
            emitter.onNext(inboundMessage);
            emitter.onComplete();
        });
        when(peerManager.messages()).thenReturn(inboundMessages);

        when(outboundEventQueueFactory.createEventQueue(anyInt(), any(Comparator.class)))
            .thenReturn(new SimplePriorityBlockingQueue<>(1, OutboundMessageEvent.comparator()));

        final var p2PConfig = P2PConfig.fromRuntimeProperties(
            Addressing.ofNetwork(Network.LOCALNET),
            new RuntimeProperties(new JSONObject(), new String[] {})
        );

        final var self = NodeId.fromPublicKey(ECKeyPair.generateNew().getPublicKey());

        MessageCentralImpl messageCentral = new MessageCentralImpl(
            self,
            self.toString(),
            messageCentralConfig,
            p2PConfig,
            new CompressedMessageSerialization(serialization),
            peerManager,
            proxyCertificateManager,
            timeSupplier,
            outboundEventQueueFactory,
            systemCounters,
            peerControl
        );

        TestObserver<String> observer = TestObserver.create();

        // when
        messageCentral.messagesOf(ConsensusEventMessage.class)
            .map(e -> Thread.currentThread().getName())
            .subscribe(observer);

        messageCentral.close();
        observer.await();

        //then
        observer.assertValue(v -> v.startsWith("MessageCentralInboundProcessor"));
    }
}
