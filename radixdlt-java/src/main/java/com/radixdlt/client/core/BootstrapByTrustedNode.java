package com.radixdlt.client.core;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.radix.serialization2.client.Serialize;

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

			return Serialize.getInstance().fromJson(universeJson, RadixUniverseConfig.class);
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
