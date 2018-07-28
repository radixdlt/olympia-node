package com.radixdlt.client.core.network;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.reactivex.observers.TestObserver;
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

}