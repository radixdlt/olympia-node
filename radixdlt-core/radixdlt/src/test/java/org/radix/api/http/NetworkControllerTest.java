/*
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
package org.radix.api.http;

import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.api.services.NetworkService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
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
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class NetworkControllerTest {
	public static final String EUID = "615243";
	private final NetworkService networkService = mock(NetworkService.class);
	private final NetworkController networkController = new NetworkController(networkService);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);
		networkController.configureRoutes(handler);

		verify(handler).get(eq("/api/network"), any());
		verify(handler).get(eq("/api/network/peers/live"), any());
		verify(handler).get(eq("/api/network/peers"), any());
		verify(handler).get(eq("/api/network/peers/{id}"), any());
	}

	@Test
	public void testRespondWithNetwork() {
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				return null;
			}
		);
		when(networkService.getNetwork()).thenReturn(jsonObject().put("response", "success"));

		networkController.respondWithNetwork(exchange);

		assertEquals("{\"response\":\"success\"}", arg.get());
	}

	@Test
	public void testRespondWithLivePeers() {
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				return null;
			}
		);
		when(networkService.getLivePeers()).thenReturn(List.of(jsonObject().put("response", "success")));

		networkController.respondWithLivePeers(exchange);

		assertEquals("[{\"response\":\"success\"}]", arg.get());
	}

	@Test
	public void testRespondWithPeers() {
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				return null;
			}
		);
		when(networkService.getPeers()).thenReturn(List.of(jsonObject().put("response", "success")));

		networkController.respondWithPeers(exchange);

		assertEquals("[{\"response\":\"success\"}]", arg.get());
	}


	@Test
	public void testRespondWithSinglePeer() {
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				return null;
			}
		);
		when(networkService.getPeer(EUID)).thenReturn(jsonObject().put("response", "success"));

		networkController.respondWithSinglePeer(exchange);

		assertEquals("{\"response\":\"success\"}", arg.get());
	}

	private static HttpServerExchange createExchange(final Answer<Void> answer) {
		var exchange = mock(HttpServerExchange.class);
		var sender = mock(Sender.class);
		var parameters = Map.<String, Deque<String>>of("id", new ArrayDeque<>(List.of(EUID)));

		doAnswer(answer).when(sender).send(anyString());
		when(exchange.getResponseHeaders()).thenReturn(mock(HeaderMap.class));
		when(exchange.getResponseSender()).thenReturn(sender);
		when(exchange.isInIoThread()).thenReturn(true);
		when(exchange.isResponseStarted()).thenReturn(true);
		when(exchange.getQueryParameters()).thenReturn(parameters);
		when(exchange.dispatch(any(Runnable.class))).thenAnswer(invocation -> {
			var runnable = invocation.getArgument(0, Runnable.class);
			runnable.run();
			return exchange;
		});

		return exchange;
	}
}