/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.network2.messaging;

import com.radixdlt.DefaultSerialization;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.crypto.CryptoException;
import com.radixdlt.serialization.Serialization;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.radix.Radix;
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
import static org.mockito.ArgumentMatchers.any;
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
    private Peer peer1;
    private Peer peer2;
    private TransportInfo transportInfo;
    private TransportMetadata transportMetadata;
    private Interfaces interfaces;

    @Before
    public void setup() {
        when(getNtpService().getUTCTimeMS()).thenAnswer((Answer<Long>) invocation -> System.currentTimeMillis());
        Serialization serialization = DefaultSerialization.getInstance();
        MessageCentralConfiguration conf = new MessagingDummyConfigurations.DummyMessageCentralConfiguration();
        interfaces = mock(Interfaces.class);
        PowerMockito.when(interfaces.isSelf(any())).thenReturn(false);

        peer1 = spy(new PeerWithSystem(getLocalSystem()));
        peer2 = spy(new PeerWithSystem(getLocalSystem()));

        AddressBook addressBook = mock(AddressBook.class);
        when(addressBook.updatePeerSystem(peer1, peer1.getSystem())).thenReturn(peer1);
        when(addressBook.updatePeerSystem(peer2, peer2.getSystem())).thenReturn(peer2);
        messageDispatcher = new MessageDispatcher(conf, serialization, () -> 30_000, getLocalSystem(), interfaces, addressBook);

        transportOutboundConnection = new MessagingDummyConfigurations.DummyTransportOutboundConnection();
        transport = new MessagingDummyConfigurations.DummyTransport(transportOutboundConnection);
        transportManager = new MessagingDummyConfigurations.DummyTransportManager(transport);
        systemMetaData = SystemMetaData.getInstance();
        reset(systemMetaData);

        transportMetadata = mock(TransportMetadata.class);
        when(transportMetadata.get("host")).thenReturn("localhost");
        transportInfo = mock(TransportInfo.class);
        when(transportInfo.metadata()).thenReturn(transportMetadata);
    }

    @Test
    public void sendSuccessfullyMessage() throws CryptoException {

        SystemMessage message = spy(new SystemMessage(getLocalSystem(), 0));
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertTrue(sendResult.isComplete());
        verify(message, times(1)).sign(getLocalSystem().getKeyPair());
    }

    @Test
    public void sendExpiredMessage() {
        Message message = spy(new TestMessage(0));
        when(message.getTimestamp()).thenReturn(10_000L);
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("org.radix.network.messages.TestMessage: TTL to " + peer1 + " has expired"));
        verify(systemMetaData, times(1)).increment("messages.outbound.aborted");
    }

    @Test
    public void sendExceptionMessage() throws CryptoException {
        SystemMessage message = spy(new SystemMessage(getLocalSystem(), 0));
        doThrow(new CryptoException("Expected exception")).when(message).sign(getLocalSystem().getKeyPair());
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, message, 10_000);

        SendResult sendResult = messageDispatcher.send(transportManager, messageEvent);

        assertFalse(sendResult.isComplete());
        assertThat(sendResult.getThrowable().getMessage(), Matchers.equalTo("org.radix.universe.system.SystemMessage: Sending to  " + peer1 + " failed"));
        assertThat(sendResult.getThrowable().getCause().getMessage(), Matchers.equalTo("Expected exception"));
    }

    @Test
    public void receiveSuccessfully() throws InterruptedException {
        SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
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
        SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
        when(testMessage.getTimestamp()).thenReturn(10_000L);
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        messageDispatcher.receive(null, messageEvent);

        //execution is terminated before message.getSystem() method
        verify(testMessage, times(0)).getSystem();
        verify(systemMetaData, times(1)).increment("messages.inbound.discarded");
    }

    @Test
    public void receiveDisconnectNullZeroSystem() {
        SystemMessage testMessage1 = spy(new SystemMessage(getLocalSystem(), 0));
        RadixSystem radixSystem1 = spy(testMessage1.getSystem());
        doReturn(radixSystem1).when(testMessage1).getSystem();
        doReturn(EUID.ZERO).when(radixSystem1).getNID();
        MessageEvent messageEvent1 = new MessageEvent(peer1, transportInfo, testMessage1, 10_000);

        SystemMessage testMessage2 = spy(new SystemMessage(getLocalSystem(), 0));
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
        SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
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
        SystemMessage testMessage = spy(new SystemMessage(getLocalSystem(), 0));
        RadixSystem radixSystem = spy(testMessage.getSystem());
        doReturn(radixSystem).when(testMessage).getSystem();
        doReturn(getLocalSystem().getNID()).when(radixSystem).getNID();
        MessageEvent messageEvent = new MessageEvent(peer1, transportInfo, testMessage, 10_000);

        messageDispatcher.receive(null, messageEvent);

        verify(peer1, times(1)).ban("Message from self");
        verify(interfaces, times(1)).addInterfaceAddress(InetAddress.getByName("localhost"));
    }

}
