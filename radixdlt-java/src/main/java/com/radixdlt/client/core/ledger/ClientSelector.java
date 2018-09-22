package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetwork;
import com.radixdlt.client.core.network.WebSocketClient.RadixClientStatus;
import io.reactivex.Single;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientSelector {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientSelector.class);

	/**
	 * The Universe we need peers for
	 */
	private final RadixUniverseConfig config;

	private final RadixNetwork radixNetwork;


	public ClientSelector(RadixUniverseConfig config, RadixNetwork radixNetwork) {
		this.config = config;
		this.radixNetwork = radixNetwork;
	}


	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given shard
	 *
	 * @param shard a shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	public Single<RadixJsonRpcClient> getRadixClient(Long shard) {
		return getRadixClient(Collections.singleton(shard));
	}

	/**
	 * Returns a cold observable of the first peer found which supports
	 * a set short shards which intersects with a given set of shards.
	 *
	 * @param shards set of shards to find an intersection with
	 * @return a cold observable of the first matching Radix client
	 */
	public Single<RadixJsonRpcClient> getRadixClient(Set<Long> shards) {
		return this.radixNetwork.getRadixClients(shards)
			.flatMapMaybe(client ->
				client.getStatus()
					.filter(status -> !status.equals(RadixClientStatus.FAILURE))
					.map(status -> client)
					.firstOrError()
					.toMaybe()
			)
			.flatMapMaybe(client ->
				client.getUniverse()
					.doOnSuccess(cliUniverse -> {
						if (!config.equals(cliUniverse)) {
							LOGGER.warn("{} has universe: {} but looking for {}",
								client, cliUniverse.getHash(), config.getHash());
						}
					})
					.map(config::equals)
					.filter(b -> b)
					.map(b -> client)
			)
			.firstOrError();
	}

}
