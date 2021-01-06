/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.reactivex.observers.TestObserver;
import java.util.function.Consumer;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.junit.Test;

import com.radixdlt.test.util.TypedMocks;

public class WebSocketClientTest {
	@Test
	public void testConnect() {
		WebSocket webSocket = mock(WebSocket.class);
		WebSocketClient client = new WebSocketClient(listener -> webSocket);
		client.connect();
	}

	@Test
	public void testConnectFail() {
		WebSocket webSocket = mock(WebSocket.class);

		WebSocketClient client = new WebSocketClient(listener -> {
			listener.onFailure(webSocket, new RuntimeException(), null);
			return webSocket;
		});

		TestObserver<WebSocketStatus> testObserver = TestObserver.create();
		client.getState().subscribe(testObserver);
		client.connect();

		testObserver.awaitCount(3);
		testObserver.assertValues(
			WebSocketStatus.DISCONNECTED,
			WebSocketStatus.CONNECTING,
			WebSocketStatus.FAILED
		);
	}

	@Test
	public void testMessage() {
		WebSocket webSocket = mock(WebSocket.class);
		Response response = mock(Response.class);

		WebSocketClient client = new WebSocketClient(listener -> {
			listener.onOpen(webSocket, response);
			listener.onMessage(webSocket, "hello");
			return webSocket;
		});

		Consumer<String> listener = TypedMocks.rmock(Consumer.class);
		client.addListener(listener);
		client.connect();
		verify(listener, times(1)).accept("hello");
	}

	@Test
	public void testMessageThenError() {
		WebSocket webSocket = mock(WebSocket.class);
		Response response = mock(Response.class);

		WebSocketClient client = new WebSocketClient(listener -> {
			listener.onOpen(webSocket, response);
			listener.onMessage(webSocket, "hello");
			listener.onFailure(webSocket, new RuntimeException(), null);
			return webSocket;
		});

		Consumer<String> listener = TypedMocks.rmock(Consumer.class);
		client.addListener(listener);
		client.connect();
		verify(listener, times(1)).accept("hello");
	}
}