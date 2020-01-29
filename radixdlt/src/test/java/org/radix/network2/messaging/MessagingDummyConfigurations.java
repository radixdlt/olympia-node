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

import com.google.common.collect.ImmutableList;

import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.network2.transport.udp.UDPConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

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

		@Override
		public int priority() {
			return 0;
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
		public Transport findTransport(Peer peer, byte[] bytes) {
			return transport;
		}
	}

}
