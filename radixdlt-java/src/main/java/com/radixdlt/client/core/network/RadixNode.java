package com.radixdlt.client.core.network;

import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixNode {
	private final Logger logger = LoggerFactory.getLogger(RadixNode.class);
	private final String location;
	private final boolean useSSL;
	private final int port;
	private final Request request;
	private final String url;

	public RadixNode(String location, boolean useSSL, int port) {
		this.location = location;
		this.useSSL = useSSL;
		this.port = port;
		this.url = (useSSL ? "wss://" : "ws://")+ location + ":" + port + "/rpc";
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
		return location;
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

		RadixNode p = (RadixNode)o;
		return p.url.equals(this.url);
	}
}
