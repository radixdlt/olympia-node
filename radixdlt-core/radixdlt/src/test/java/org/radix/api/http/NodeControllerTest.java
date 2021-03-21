package org.radix.api.http;/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.chaos.mempoolfiller.InMemoryWallet;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.engine.RadixEngine;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import com.radixdlt.application.validator.ValidatorRegistration;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Base58;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

public class NodeControllerTest {
	private final RadixEngine<LedgerAtom, VerifiedLedgerHeaderAndProof> radixEngine = mock(RadixEngine.class);
	private RadixAddress radixAddress = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	private EventDispatcher<ValidatorRegistration> dispatcher = mock(EventDispatcher.class);
	private NodeController nodeController = new NodeController(radixAddress, radixEngine, dispatcher);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);
		nodeController.configureRoutes(handler);

		verify(handler).post(eq("/node/validator"), any());
		verify(handler).get(eq("/node"), any());
	}

	@Test
	public void testRespondWithNode() throws InterruptedException {
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();

		String nodeKey = Base58.toBase58(ECKeyPair.generateNew().getPublicKey().getBytes());
		var exchange = createExchange(
			"{}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);


		var wallet = mock(InMemoryWallet.class);
		when(radixEngine.getComputedState(InMemoryWallet.class)).thenReturn(wallet);
		when(wallet.getBalance()).thenReturn(BigDecimal.ONE);
		when(wallet.getNumParticles()).thenReturn(7);

		nodeController.respondWithNode(exchange);

		latch.await();
		assertEquals(
			"{\"address\":\"23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x\",\"balance\":1,\"numParticles\":7}",
			arg.get()
		);
	}

	@Test
	public void testHandleValidatorRegistration() throws InterruptedException {
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();

		String nodeKey = Base58.toBase58(ECKeyPair.generateNew().getPublicKey().getBytes());
		var exchange = createExchange(
			"{ \"enabled\" : true}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);

		nodeController.handleValidatorRegistration(exchange);

		latch.await();
		assertEquals("{}", arg.get());
		verify(dispatcher).dispatch(eq(ValidatorRegistration.register()));
	}

	@Test
	public void testHandleValidatorUnRegistration() throws InterruptedException {
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();

		String nodeKey = Base58.toBase58(ECKeyPair.generateNew().getPublicKey().getBytes());
		var exchange = createExchange(
			"{ \"enabled\" : false}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);

		nodeController.handleValidatorRegistration(exchange);

		latch.await();
		assertEquals("{}", arg.get());
		verify(dispatcher).dispatch(eq(ValidatorRegistration.unregister()));
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