/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.test;

import com.google.common.collect.ImmutableSet;
import okhttp3.HttpUrl;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

// TODO ideally should be able to manage a clusters lifecycle the same way a docker net is managed
public class StaticRemoteBFTNetwork implements RemoteBFTNetwork {
	private final Set<String> nodeIps;

	private StaticRemoteBFTNetwork(Set<String> nodeIps) {
		this.nodeIps = nodeIps;
	}

	@Override
	public HttpUrl getEndpointUrl(String nodeId, String endpoint) {
		if (!nodeIps.contains(nodeId)) {
			throw new IllegalArgumentException("unknown nodeId: " + nodeId);
		}

		String endpointUrl = String.format("http://%s/%s", nodeId, endpoint);
		return HttpUrl.parse(endpointUrl);
	}

	@Override
	public Set<String> getNodeIds() {
		return nodeIps; // TODO is using node IPs as their ids fine?
	}

	public static StaticRemoteBFTNetwork from(Collection<String> nodeIps) {
		Objects.requireNonNull(nodeIps);
		if (nodeIps.isEmpty()) {
			throw new IllegalArgumentException("network must contain at least one node ip");
		}
		return new StaticRemoteBFTNetwork(ImmutableSet.copyOf(nodeIps));
	}
}
