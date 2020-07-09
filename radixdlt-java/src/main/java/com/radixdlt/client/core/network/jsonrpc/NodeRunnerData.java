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

package com.radixdlt.client.core.network.jsonrpc;

import java.util.Map;

import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

@SerializerId2("network.peer")
public class NodeRunnerData {
	private String ip;

	@JsonProperty("system")
	@DsonOutput(Output.ALL)
	private RadixSystem system;

	// Placeholder for the serializer ID
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("version")
	@DsonOutput(DsonOutput.Output.ALL)
	private short version = 100;

	NodeRunnerData() {
		// No-arg constructor for serializer
	}

	protected NodeRunnerData(RadixSystem system) {
		this.ip = null;
		this.system = system;
	}

	public String getIp() {
		return ip;
	}

	@Override
	public String toString() {
		return (ip != null ? (ip + ": ") : "");
	}

	@Override
	public int hashCode() {
		// TODO: fix hack
		return ip.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof NodeRunnerData)) {
			return false;
		}

		NodeRunnerData other = (NodeRunnerData) o;
		return other.ip.equals(ip);
	}

	// Property "host" - 1 getter, 1 setter
	// Could potentially just serialize the URI as a string
	@JsonProperty("host")
	@DsonOutput(Output.ALL)
	private Map<String, Object> getJsonHost() {
		return ImmutableMap.of("ip", this.ip);
	}

	@JsonProperty("host")
	private void setJsonHost(Map<String, Object> props) {
		this.ip = props.get("ip").toString();
	}
}
