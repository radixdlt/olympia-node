package org.radix.network2.messaging;

import com.radixdlt.common.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.Serialization;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.Radix;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.messages.TestMessage;
import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.AddressBook;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.addressbook.PeerWithSystem;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.serialization.RadixTest;
import org.radix.time.NtpService;
import org.radix.universe.system.LocalSystem;
import org.radix.universe.system.RadixSystem;
import org.radix.universe.system.SystemMessage;
import org.radix.utils.SystemMetaData;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MessageDispatcherTest extends RadixTest {

    private MessageDispatcher messageDispatcher;
    private Transport transport;
    private TransportManager transportManager;
    private TransportOutboundConnection transportOutboundConnection;
    private SystemMetaData systemMetaData;
    private NtpService ntpService;
    private Peer peer1;
    private Peer peer2;
    private TransportInfo transportInfo;
    private TransportMetadata transportMetadata;
    private Interfaces interfaces;

    @Before
    public void setup() {
        ntpService = Modules.get(NtpService.class);
        when(ntpService.getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());
        Serialization serialization = Serialization.getDefault();
        MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();
        messageDispatcher = new MessageDispatcher(conf, serialization, () -> 30_000);
        transportOutboundConnection = new MessagingDummyConfigurations.DummyTransportOutboundConnection();
        transport = new MessagingDummyConfigurations.DummyTransport(transportOutboundConnection);
        transportManager = new MessagingDummyConfigurations.DummyTransportManager(transport);
        systemMetaData = Modules.get(SystemMetaData.class);
        reset(systemMetaData);

        transportMetadata = mock(TransportMetadata.class);
        when(transportMetadata.get("host")).thenReturn("localhost");
        transportInfo = mock(TransportInfo.class);
        when(transportInfo.metadata()).thenReturn(transportMetadata);

        RadixSystem radixSystem = new RadixSystem(LocalSystem.getInstance());
        peer1 = spy(new PeerWithSystem(radixSystem));
        peer2 = spy(new PeerWithSystem(radixSystem));

        AddressBook addressBook = mock(AddressBook.class);
        when(addressBook.updatePeerSystem(peer1, peer1.getSystem())).thenReturn(peer1);
        when(addressBook.updatePeerSystem(peer2, peer2.getSystem())).thenReturn(peer2);
        Modules.put(AddressBook.class, addressBook);

        interfaces = mock(Interfaces.class);
        Modules.put(Interfaces.class, interfaces);
    }

    @After
    public void teardown() {
        Modules.remove(AddressBook.class);
        Modules.remove(Interfaces.class);
    }

    @Test
    public void sendSuccessfullyMessage() throws CryptoException {

        SystemMessage message = spy(new SystemMessage());
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertTrue(sendResult.isComplete());
        verify(message, times(1)).sign(LocalSystem.getInstance().getKeyPair());
    }

    @Test
    public void sendExpiredMessage() {
        Message message = spy(new TestMessage());
        when(message.getTimestamp()).thenReturn(10_000L);
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("org.radix.network.messages.TestMessage: TTL to " + peer1 + " has expired"));
        verify(systemMetaData, times(1)).increment("messages.outbound.aborted");
    }

    @Test
    public void sendExceptionMessage() throws CryptoException {
        SystemMessage message = spy(new SystemMessage());
        doThrow(new CryptoException("Expected exception")).when(message).sign(LocalSystem.getInstance().getKeyPair());
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertFalse(sendResult.isComplete());
        assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("org.radix.universe.system.SystemMessage: Sending to  " + peer1 + " failed"));
        assertThat(sendResult.getThrowable().getCause().getMessage(), Matchers.equalTo("Expected exception"));
    }

    @Test
    public void receiveSuccessfully() throws InterruptedException {
        SystemMessage testMessage = spy(new SystemMessage());
        RadixSystem radixSystem = spy(testMessage.getSystem());
        doReturn(radixSystem).when(testMessage).getSystem();
        doReturn(EUID.ONE).when(radixSystem).getNID();

        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        Semaphore receivedFlag = new Semaphore(0);
        List<Message> messages = new ArrayList<>();
        MessageListenerList messageListenerList = new MessageListenerList();
        messageListenerList.addMessageListener((source, message) -> {
            messages.add(message);
            receivedFlag.release();
        });

        messageDispatcher.receive(messageListenerList, messageEvent);

        assertTrue(receivedFlag.tryAcquire(10, TimeUnit.SECONDS));
        assertThat(messages.get(0), Matchers.equalTo(testMessage));
    }

    @Test
    public void receiveExpiredMessage() {
        SystemMessage testMessage = spy(new SystemMessage());
        when(testMessage.getTimestamp()).thenReturn(10_000L);
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        messageDispatcher.receive(null, messageEvent);

        //execution is terminated before message.getSystem() method
        verify(testMessage, times(0)).getSystem();
        verify(systemMetaData, times(1)).increment("messages.inbound.discarded");
    }

    @Test
    public void receiveDisconnectNullZeroSystem() {
        SystemMessage testMessage1 = spy(new SystemMessage());
        RadixSystem radixSystem1 = spy(testMessage1.getSystem());
        doReturn(radixSystem1).when(testMessage1).getSystem();
        doReturn(EUID.ZERO).when(radixSystem1).getNID();
        MessageEvent messageEvent1 = new MessageEvent(peer1, transportInfo, testMessage1, 10_000);

        SystemMessage testMessage2 = spy(new SystemMessage());
        RadixSystem radixSystem2 = spy(testMessage2.getSystem());
        doReturn(radixSystem2).when(testMessage2).getSystem();
        doReturn(null).when(radixSystem2).getNID();
        MessageEvent messageEvent2 = new MessageEvent(peer2, transportInfo, testMessage2, 10_000);

        messageDispatcher.receive(null, messageEvent1);
        messageDispatcher.receive(null, messageEvent2);

        String banMessage = "%s:org.radix.universe.system.SystemMessage gave null NID";
        String msg1 = String.format(banMessage, peer1);
        String msg2 = String.format(banMessage, peer2);
        verify(peer1, times(1)).ban(msg1);
        verify(peer2, times(1)).ban(msg2);
    }

    @Test
    public void receiveDisconnectOldPeer() {
        SystemMessage testMessage = spy(new SystemMessage());
        RadixSystem radixSystem = spy(testMessage.getSystem());
        doReturn(radixSystem).when(testMessage).getSystem();
        doReturn(EUID.ONE).when(radixSystem).getNID();
        doReturn(Radix.REFUSE_AGENT_VERSION).when(radixSystem).getAgentVersion();
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        messageDispatcher.receive(null, messageEvent);

        String banMessage = "Old peer " + peer1 + " /Radix:/2710000:100";
        verify(peer1, times(1)).ban(banMessage);
    }

    @Test
    public void receiveBanSelf() throws UnknownHostException {
        SystemMessage testMessage = spy(new SystemMessage());
        RadixSystem radixSystem = spy(testMessage.getSystem());
        doReturn(radixSystem).when(testMessage).getSystem();
        doReturn(LocalSystem.getInstance().getNID()).when(radixSystem).getNID();
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        messageDispatcher.receive(null, messageEvent);

        verify(peer1, times(1)).ban("Message from self");
        verify(interfaces, times(1)).addInterfaceAddress(InetAddress.getByName("localhost"));
    }

}
