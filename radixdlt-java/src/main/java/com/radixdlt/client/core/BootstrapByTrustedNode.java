package com.radixdlt.client.core;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
	private final RadixNode trustedNode;
	private final ConcurrentMap<String, RadixUniverseConfig> memoizer = new ConcurrentHashMap<>();

	public BootstrapByTrustedNode(RadixNode trustedNode) {
		this.trustedNode = trustedNode;
	}

	@Override
	public RadixUniverseConfig getConfig() {
		return memoizer.computeIfAbsent("", s -> {
			final OkHttpClient client = new OkHttpClient();
			final Call call = client.newCall(trustedNode.getHttpEndpoint("/api/universe"));
			final String universeJson;
			try (Response response = call.execute()) {
				ResponseBody body = response.body();
				if (body == null) {
					throw new IllegalStateException("Could not retrieve universe configuration.");
				}
				universeJson = body.string();
			} catch (IOException e) {
				throw new IllegalStateException("Could not retrieve universe configuration.");
			}

			return Serialize.getInstance().fromJson(universeJson, RadixUniverseConfig.class);
		});
	}

	@Override
	public List<RadixNetworkEpic> getDiscoveryEpics() {
		return Collections.emptyList();
	}

	@Override
	public Set<RadixNode> getInitialNetwork() {
		return ImmutableSet.of(trustedNode);
	}
}
