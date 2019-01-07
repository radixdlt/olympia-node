package com.radixdlt.client.core.network.websocket;

import java.io.IOException;

public class WebSocketException extends IOException {

	public WebSocketException() {
		super();
	}

	public WebSocketException(String message) {
		super(message);
	}
}
