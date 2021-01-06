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

	public RadixNode(Request rootRequestLocation) {
		this(
			rootRequestLocation.url().host(),
			rootRequestLocation.isHttps(),
			rootRequestLocation.url().port()
		);
	}

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
	 * @param path node-local part of URL
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
