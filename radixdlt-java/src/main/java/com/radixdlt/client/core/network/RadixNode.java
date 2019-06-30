package com.radixdlt.client.core.network;

import okhttp3.Request;

/**
 * Unique network node endpoint.
 */
public final class RadixNode {
	private final String location;
	private final boolean useSSL;
	private final int port;
	private final Request webSocketEndpoint;
	private final String webSocketUrl;
	private final String httpUrl;

	public RadixNode(String location, boolean useSSL, int port) {
		this.location = location;
		this.useSSL = useSSL;
		this.port = port;
		this.webSocketUrl = (useSSL ? "wss://" : "ws://") + location + ":" + port + "/rpc";
		this.webSocketEndpoint = new Request.Builder().url(webSocketUrl).build();
		this.httpUrl = (useSSL ? "https://" : "http://") + location + ":" + port;
	}

	/**
	 * Get the websocket URL for this radix node
	 * @return request object pointing to the websocket endpoint
	 */
	public Request getWebSocketEndpoint() {
		return webSocketEndpoint;
	}

	/**
	 * Get the http URL for this radix node
	 * @return request object pointing to the http endpoint
	 */
	public Request getHttpEndpoint(String path) {
		return new Request.Builder().url(this.httpUrl + path).build();
	}

	public int getPort() {
		return port;
	}

	public boolean isSsl() {
		return useSSL;
	}

	@Override
	public String toString() {
		return location + ":" + port;
	}

	@Override
	public int hashCode() {
		return webSocketUrl.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RadixNode)) {
			return false;
		}

		RadixNode p = (RadixNode) o;
		return p.webSocketUrl.equals(this.webSocketUrl);
	}
}
