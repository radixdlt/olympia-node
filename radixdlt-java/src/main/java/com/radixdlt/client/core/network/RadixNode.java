package com.radixdlt.client.core.network;

import okhttp3.Request;

/**
 * Unique network node endpoint.
 */
public final class RadixNode {
	private final String location;
	private final boolean useSSL;
	private final int port;
	private final Request request;
	private final String url;

	public RadixNode(String location, boolean useSSL, int port) {
		this.location = location;
		this.useSSL = useSSL;
		this.port = port;
		this.url = (useSSL ? "wss://" : "ws://") + location + ":" + port + "/rpc";
		this.request = new Request.Builder().url(url).build();
	}

	public Request getLocation() {
		return request;
	}

	public int getPort() {
		return port;
	}

	public boolean isSsl() {
		return useSSL;
	}

	@Override
	public String toString() {
		return url;
	}

	@Override
	public int hashCode() {
		return url.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof RadixNode)) {
			return false;
		}

		RadixNode p = (RadixNode) o;
		return p.url.equals(this.url);
	}
}
