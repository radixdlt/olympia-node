package org.radix.network2.messaging;

import com.google.common.collect.ImmutableList;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.network2.transport.udp.UDPConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

public class MessagingDummyConfigurations {
    public static class DummyMessageCentralConfiguration implements MessageCentralConfiguration {
        @Override
        public int messagingInboundQueueMax(int defaultValue) {
            return 10;
        }

        @Override
        public int messagingOutboundQueueMax(int defaultValue) {
            return 11;
        }

        @Override
        public int messagingTimeToLive(int defaultValue) {
            return 10;
        }

        @Override
        public int messagingInboundQueueThreads(int defaultValue) {
            return 1;
        }

        @Override
        public int messagingOutboundQueueThreads(int defaultValue) {
            return 1;
        }
    }

    public static class DummyTransport implements Transport {
        private InboundMessageConsumer messageSink = null;
        private boolean isClosed = false;

        private final TransportOutboundConnection out;

        public DummyTransport(TransportOutboundConnection out) {
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
            return UDPConstants.UDP_NAME;
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

        public InboundMessageConsumer getMessageSink() {
            return messageSink;
        }

        public boolean isClosed() {
            return isClosed;
        }

        public TransportOutboundConnection getOut() {
            return out;
        }

		@Override
		public boolean canHandle(byte[] message) {
			return true;
		}
    }

    public static class DummyTransportOutboundConnection implements TransportOutboundConnection {
        private boolean sent = false;

        private final List<byte[]> messages = new ArrayList<>();
        private volatile CountDownLatch countDownLatch;

        public DummyTransportOutboundConnection() {
            this.countDownLatch = new CountDownLatch(1);
        }

        @Override
        public void close() throws IOException {
            // Ignore for now
        }

        @Override
        public CompletableFuture<SendResult> send(byte[] data) {
            sent = true;
            messages.add(data);
            countDownLatch.countDown();
            return CompletableFuture.completedFuture(SendResult.complete());
        }

        public List<byte[]> getMessages() {
            return messages;
        }

        public void setCountDownLatch(CountDownLatch countDownLatch) {
            this.countDownLatch = countDownLatch;
        }

        public boolean isSent() {
            return sent;
        }

        public CountDownLatch getCountDownLatch() {
            return countDownLatch;
        }
    }

    public static class DummyTransportManager implements TransportManager {
        private Transport transport;

        public DummyTransportManager(Transport transport) {
            this.transport = transport;
        }

        @Override
        public void close() throws IOException {
            // Nothing
        }

        @Override
        public List<Transport> transports() {
            return ImmutableList.of(transport);
        }

        @Override
		public Transport findTransport(Stream<TransportInfo> peerTransports, byte[] bytes) {
            return transport;
        }
    }

}
