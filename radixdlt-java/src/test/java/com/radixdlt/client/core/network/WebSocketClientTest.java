package com.radixdlt.client.core.network;

import static org.mockito.Mockito.mock;

import io.reactivex.observers.TestObserver;
import java.io.IOException;
import okhttp3.Response;
import okhttp3.WebSocket;
import org.junit.Test;

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

		TestObserver<RadixClientStatus> testObserver = TestObserver.create();
		client.getState().subscribe(testObserver);
		client.connect();

		testObserver.awaitCount(3);
		testObserver.assertValues(
			RadixClientStatus.DISCONNECTED,
			RadixClientStatus.CONNECTING,
			RadixClientStatus.FAILED
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

		TestObserver<String> testObserver = TestObserver.create();
		client.getMessages().subscribe(testObserver);
		client.connect();
		testObserver.assertValue("hello");
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

		TestObserver<String> testObserver = TestObserver.create();
		client.getMessages().subscribe(testObserver);
		client.connect();
		testObserver.assertNoErrors();
		testObserver.assertValues("hello");
	}
}