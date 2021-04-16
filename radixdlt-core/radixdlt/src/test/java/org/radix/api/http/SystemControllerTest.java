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

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.radix.api.services.SystemService;
import org.radix.time.Time;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.HeaderMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class SystemControllerTest {
	private final SystemService systemService = mock(SystemService.class);
	private final InMemorySystemInfo inMemorySystemInfo = mock(InMemorySystemInfo.class);
	private final Universe universe =
		Universe.newBuilder()
			.type(UniverseType.DEVELOPMENT)
			.build();
	private final SystemController systemController =
		new SystemController(systemService, inMemorySystemInfo, universe);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);
		systemController.configureRoutes(handler);

		verify(handler).get(eq("/api/system"), any());
		verify(handler).get(eq("/api/ping"), any());
		verify(handler).put(eq("/api/bft/0"), any());
		verify(handler).get(eq("/api/universe"), any());
		verify(handler).get(eq("/api/vertices/highestqc"), any());
	}

	@Test
	public void testPingEntryPoint() {
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			"{\"id\":321, \"method\":\"Ping\"}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				return null;
			}
		);
		when(systemService.getPong()).thenReturn(jsonObject().put("response", "pong").put("timestamp", Time.currentTimestamp()));

		systemController.respondWithPong(exchange);

		assertNotNull(arg.get());

		var obj = new JSONObject(arg.get());
		assertNotNull(obj);
		assertEquals("pong", obj.getString("response"));
	}

	@Test
	public void testHandleBftStateStart() throws InterruptedException {
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			"{\"id\":321, \"method\":\"BFT.start\", \"state\":true}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);
		when(systemService.bftStart()).thenReturn(jsonObject().put("response", "success"));

		systemController.handleBftState(exchange);

		latch.await();
		assertEquals("{\"response\":\"success\"}", arg.get());
	}

	@Test
	public void testHandleBftStateStop() throws InterruptedException {
		var latch = new CountDownLatch(1);
		var arg = new AtomicReference<String>();
		var exchange = createExchange(
			"{\"id\":321, \"method\":\"BFT.stop\", \"state\":true}",
			invocation -> {
				arg.set(invocation.getArgument(0, String.class));
				latch.countDown();
				return null;
			}
		);
		when(systemService.bftStart()).thenReturn(jsonObject().put("response", "success"));

		systemController.handleBftState(exchange);

		latch.await();
		assertEquals("{\"response\":\"success\"}", arg.get());
	}

	private static HttpServerExchange createExchange(final String json, final Answer<Void> answer) {
		var exchange = mock(HttpServerExchange.class);
		var sender = mock(Sender.class);

		doAnswer(answer).when(sender).send(anyString());
		when(exchange.getResponseHeaders()).thenReturn(mock(HeaderMap.class));
		when(exchange.getResponseSender()).thenReturn(sender);
		when(exchange.getInputStream()).thenReturn(asStream(json));
		when(exchange.isInIoThread()).thenReturn(true);
		when(exchange.isResponseStarted()).thenReturn(true);
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