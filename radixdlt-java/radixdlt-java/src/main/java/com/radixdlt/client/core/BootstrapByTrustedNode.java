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

package com.radixdlt.client.core;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.serialization.DeserializeException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.radixdlt.client.serialization.Serialize;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Bootstrap configuration by a trusted node. Universe configuration file will be retrieved from
 * the node and no other nodes will be connected to.
 */
public class BootstrapByTrustedNode implements BootstrapConfig {
	private final ImmutableSet<RadixNode> trustedNodes;
	private final Supplier<RadixUniverseConfig> memoizer;

	public BootstrapByTrustedNode(Collection<RadixNode> trustedNodes) {
		if (trustedNodes == null || trustedNodes.isEmpty()) {
			throw new IllegalArgumentException("At least one trusted node must be specified");
		}
		this.trustedNodes = ImmutableSet.copyOf(trustedNodes);
		RadixNode firstNode = this.trustedNodes.iterator().next();
		this.memoizer = Suppliers.memoize(() -> {
			final OkHttpClient client = HttpClients.getSslAllTrustingClient();
			final Call call = client.newCall(firstNode.getHttpEndpoint("/api/universe"));
			final String universeJson;
			try (Response response = call.execute();
				ResponseBody body = response.body()) {
				if (body == null) {
					throw new IllegalStateException("Could not retrieve universe configuration.");
				}
				universeJson = body.string();
			} catch (IOException e) {
				throw new IllegalStateException("Could not retrieve universe configuration.", e);
			}

			try {
				return Serialize.getInstance().fromJson(universeJson, RadixUniverseConfig.class);
			} catch (DeserializeException e) {
				throw new IllegalStateException("Failed to deserialize", e);
			}
		});
	}

	public BootstrapByTrustedNode(RadixNode singleNode) {
		this(ImmutableSet.of(singleNode));
	}

	@Override
	public RadixUniverseConfig getConfig() {
		return memoizer.get();
	}

	@Override
	public List<RadixNetworkEpic> getDiscoveryEpics() {
		return Collections.emptyList();
	}

	@Override
	public Set<RadixNode> getInitialNetwork() {
		return this.trustedNodes;
	}
}
