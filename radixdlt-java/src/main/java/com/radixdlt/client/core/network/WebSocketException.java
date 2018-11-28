package com.radixdlt.client.core.network;

import java.io.IOException;

public class WebSocketException extends IOException {

	public WebSocketException() {
		super();
	}

	public WebSocketException(String message) {
		super(message);
	}
}
