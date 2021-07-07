/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

public final class NetworkChannel {
	private final long localPort;
	private final String ip;
	private final ChannelType type;
	private final String uri;

	private NetworkChannel(long localPort, String ip, ChannelType type, String uri) {
		this.localPort = localPort;
		this.ip = ip;
		this.type = type;
		this.uri = uri;
	}

	@JsonCreator
	public static NetworkChannel create(
		@JsonProperty(value = "localPort", required = true) long localPort,
		@JsonProperty(value = "ip", required = true) String ip,
		@JsonProperty(value = "type", required = true) ChannelType type,
		@JsonProperty("uri") String uri
	) {
		return new NetworkChannel(localPort, ip, type, uri);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (!(o instanceof NetworkChannel)) {
			return false;
		}

		var that = (NetworkChannel) o;
		return localPort == that.localPort
			&& ip.equals(that.ip)
			&& type == that.type
			&& Objects.equals(uri, that.uri);
	}

	@Override
	public int hashCode() {
		return Objects.hash(localPort, ip, type, uri);
	}

	@Override
	public String toString() {
		return "{"
			+ "localPort=" + localPort
			+ ", ip='" + ip + '\''
			+ ", type=" + type
			+ ", uri='" + (uri == null ? "none" : uri) + '\''
			+ '}';
	}


	public long getLocalPort() {
		return localPort;
	}

	public String getIp() {
		return ip;
	}

	public ChannelType getType() {
		return type;
	}

	public Optional<String> getUri() {
		return Optional.ofNullable(uri);
	}
}
