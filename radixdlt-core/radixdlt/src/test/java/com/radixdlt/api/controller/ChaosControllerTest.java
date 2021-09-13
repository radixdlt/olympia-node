/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */
package com.radixdlt.api.controller;

import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import com.radixdlt.api.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.api.chaos.messageflooder.MessageFlooderUpdate;
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
	@SuppressWarnings("unchecked")
	private final EventDispatcher<MempoolFillerUpdate> mempool = mock(EventDispatcher.class);
	@SuppressWarnings("unchecked")
	private final EventDispatcher<MessageFlooderUpdate> message = mock(EventDispatcher.class);
	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);

	@Test
	@Ignore
	public void routesAreConfigured() {
		final ChaosController chaosController = new ChaosController(mempool, message, addressing);
		var handler = mock(RoutingHandler.class);
		chaosController.configureRoutes("/root/", handler);

		verify(handler).put(eq("/root/message-flooder"), any());
		verify(handler).put(eq("/root/mempool-filler"), any());
	}

	@Test
	@Ignore
	public void testHandleMessageFlood() throws InterruptedException {
		final ChaosController chaosController = new ChaosController(mempool, message, addressing);
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();

		var nodeAddress = addressing.forValidators().of(ECKeyPair.generateNew().getPublicKey());
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
		when(exchange.getRequestHeaders()).thenReturn(mock(HeaderMap.class));
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
