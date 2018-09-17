package com.radixdlt.client.core.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.reactivex.observers.TestObserver;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.junit.Test;

public class WebSocketClientTest {
	@Test
	public void testConnect() {
		OkHttpClient okHttpClient = mock(OkHttpClient.class);
		WebSocket webSocket = mock(WebSocket.class);
		Request request = mock(Request.class);
		Response response = mock(Response.class);

		WebSocketClient client = new WebSocketClient(() -> okHttpClient, request);
		doAnswer(invocation -> {
			WebSocketListener listener = (WebSocketListener) invocation.getArguments()[1];
			listener.onOpen(webSocket, response);
			return webSocket;
		}).when(okHttpClient).newWebSocket(any(), any());

		TestObserver testObserver = TestObserver.create();
		client.connect().subscribe(testObserver);
		testObserver.assertComplete();
	}

	@Test
	public void testConnectFail() {
		OkHttpClient okHttpClient = mock(OkHttpClient.class);
		WebSocket webSocket = mock(WebSocket.class);
		Request request = mock(Request.class);

		WebSocketClient client = new WebSocketClient(() -> okHttpClient, request);
		doAnswer(invocation -> {
			WebSocketListener listener = (WebSocketListener) invocation.getArguments()[1];
			listener.onFailure(webSocket, new RuntimeException(), null);
			return webSocket;
		}).when(okHttpClient).newWebSocket(any(), any());

		TestObserver testObserver = TestObserver.create();
		client.connect().subscribe(testObserver);
		testObserver.assertError(t -> t instanceof RuntimeException);
	}

	@Test
	public void testMessage() {
		OkHttpClient okHttpClient = mock(OkHttpClient.class);
		WebSocket webSocket = mock(WebSocket.class);
		Request request = mock(Request.class);
		Response response = mock(Response.class);

		WebSocketClient client = new WebSocketClient(() -> okHttpClient, request);
		doAnswer(invocation -> {
			WebSocketListener listener = (WebSocketListener) invocation.getArguments()[1];
			listener.onOpen(webSocket, response);
			listener.onMessage(webSocket, "hello");
			return webSocket;
		}).when(okHttpClient).newWebSocket(any(), any());

		TestObserver<String> testObserver = TestObserver.create();
		client.getMessages().subscribe(testObserver);
		client.connect().subscribe();

		testObserver.assertValue("hello");
	}

	@Test
	public void testMessageThenError() {
		OkHttpClient okHttpClient = mock(OkHttpClient.class);
		WebSocket webSocket = mock(WebSocket.class);
		Request request = mock(Request.class);
		Response response = mock(Response.class);

		WebSocketClient client = new WebSocketClient(() -> okHttpClient, request);
		doAnswer(invocation -> {
			WebSocketListener listener = (WebSocketListener) invocation.getArguments()[1];
			listener.onOpen(webSocket, response);
			listener.onMessage(webSocket, "hello");
			listener.onFailure(webSocket, new RuntimeException(), null);
			return webSocket;
		}).when(okHttpClient).newWebSocket(any(), any());

		TestObserver<String> testObserver = TestObserver.create();
		client.getMessages().subscribe(testObserver);
		client.connect().subscribe();

		testObserver.assertFailure(IOException.class, "hello");
	}
}