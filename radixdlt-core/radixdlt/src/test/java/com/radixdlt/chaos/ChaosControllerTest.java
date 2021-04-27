/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */
package com.radixdlt.chaos;

import com.radixdlt.identifiers.ValidatorAddress;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.HeaderMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChaosControllerTest {
	private final EventDispatcher<MempoolFillerUpdate> mempool = mock(EventDispatcher.class);
	private final EventDispatcher<MessageFlooderUpdate> message = mock(EventDispatcher.class);

	@Test
	public void routesAreConfigured() {
		final ChaosController chaosController = new ChaosController(mempool, message);
		var handler = mock(RoutingHandler.class);
		chaosController.configureRoutes(handler);

		verify(handler).put(eq("/chaos/message-flooder"), any());
		verify(handler).put(eq("/chaos/mempool-filler"), any());
	}

	@Test
	public void testHandleMessageFlood() throws InterruptedException {
		final ChaosController chaosController = new ChaosController(mempool, message);
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();

		var nodeAddress = ValidatorAddress.of(ECKeyPair.generateNew().getPublicKey());
		var exchange = createExchange(
			"{ \"enabled\" : true, \"data\" : { \"nodeAddress\" : \""
				+ nodeAddress
				+ "\", \"messagesPerSec\" : 10, \"commandSize\" : 123 }}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);

		chaosController.handleMessageFlood(exchange);

		latch.await();
		assertEquals("{}", arg.get());

		var captor = ArgumentCaptor.forClass(MessageFlooderUpdate.class);
		verify(message).dispatch(captor.capture());

		var value = captor.getValue();
		assertEquals(Optional.of(10), value.getMessagesPerSec());
		assertEquals(Optional.of(123), value.getCommandSize());
	}

	private static HttpServerExchange createExchange(final String json, final Answer<Void> answer) {
		var exchange = mock(HttpServerExchange.class);
		var sender = mock(Sender.class);

		doAnswer(answer).when(sender).send(anyString());
		when(exchange.getResponseHeaders()).thenReturn(mock(HeaderMap.class));
		when(exchange.getResponseSender()).thenReturn(sender);
		when(exchange.getInputStream()).thenReturn(asStream(json));
		when(exchange.isInIoThread()).thenReturn(true);
		when(exchange.isResponseStarted()).thenReturn(false);
		when(exchange.dispatch(any(Runnable.class))).thenAnswer(invocation -> {
			var runnable = invocation.getArgument(0, Runnable.class);
			runnable.run();
			return exchange;
		});

		return exchange;
	}

	private static InputStream asStream(final String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
